package com.fintrack.workerservice.idempotency.service;

import com.fintrack.workerservice.idempotency.repository.ProcessedMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Testcontainers
@DataJpaTest(
        showSql = false,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.datasource.hikari.connection-init-sql=SET lock_timeout TO 250"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProcessedMessageService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProcessedMessageLockTimeoutIntegrationTest {

    private static final UUID EVENT_ID =
            UUID.fromString("8fb4e595-dbbc-4b7f-a791-8902bf5d93e1");

    private static final String CONSUMER_NAME = "transaction-created-processor";
    private static final String EVENT_TYPE = "TRANSACTION_CREATED";
    private static final int EVENT_VERSION = 1;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ProcessedMessageService processedMessageService;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        jdbcTemplate.execute("""
                ALTER TABLE processed_message
                ALTER COLUMN processed_at
                SET DEFAULT clock_timestamp()
                """);

        jdbcTemplate.execute("TRUNCATE TABLE processed_message RESTART IDENTITY");
    }

    @Test
    void competingDuplicateInsertStopsAtConfiguredLockTimeout() throws Exception {
        assertThat(jdbcTemplate.queryForObject("SHOW lock_timeout", String.class))
                .isEqualTo("250ms");

        CountDownLatch firstInsertCompleted = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> firstTransaction = executor.submit(() ->
                    transactionTemplate.execute(status -> {
                        boolean inserted = processedMessageService.recordIfFirst(
                                EVENT_ID,
                                CONSUMER_NAME,
                                EVENT_TYPE,
                                EVENT_VERSION
                        );

                        firstInsertCompleted.countDown();
                        await(releaseFirstTransaction);

                        return inserted;
                    })
            );

            assertThat(firstInsertCompleted.await(5, TimeUnit.SECONDS)).isTrue();

            Instant competingInsertStartedAt = Instant.now();

            Future<Boolean> competingTransaction = executor.submit(() ->
                    transactionTemplate.execute(status ->
                            processedMessageService.recordIfFirst(
                                    EVENT_ID,
                                    CONSUMER_NAME,
                                    EVENT_TYPE,
                                    EVENT_VERSION
                            )
                    )
            );

            Throwable futureFailure;

            try {
                futureFailure = catchThrowable(() ->
                        competingTransaction.get(3, TimeUnit.SECONDS));
            } finally {
                releaseFirstTransaction.countDown();
            }

            long elapsedMilliseconds =
                    Duration.between(competingInsertStartedAt, Instant.now()).toMillis();

            assertThat(firstTransaction.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(futureFailure).isInstanceOf(ExecutionException.class);

            Throwable databaseFailure = futureFailure.getCause();

            assertThat(databaseFailure).isInstanceOf(DataAccessException.class);
            assertThat(rootCause(databaseFailure)).isInstanceOf(SQLException.class);
            assertThat(((SQLException) rootCause(databaseFailure)).getSQLState())
                    .isEqualTo("55P03");

            assertThat(elapsedMilliseconds)
                    .isGreaterThanOrEqualTo(100)
                    .isLessThan(3000);

            assertThat(processedMessageRepository.count()).isEqualTo(1);
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting to release the first transaction"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Interrupted while holding the first transaction",
                    exception
            );
        }
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }
}