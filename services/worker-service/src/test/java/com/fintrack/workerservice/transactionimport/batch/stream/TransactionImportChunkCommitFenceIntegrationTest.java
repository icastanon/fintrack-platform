package com.fintrack.workerservice.transactionimport.batch.stream;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportProcessingLeaseLostException;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingLeaseAcquisition;
import com.fintrack.workerservice.transactionimport.service.TransactionImportProcessingLeaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest(
        showSql = false,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "fintrack.batch.import-processing-lease-duration=30s"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TransactionImportProcessingLeaseManager.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransactionImportChunkCommitFenceIntegrationTest {

    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 52L;
    private static final Long USER_ID = 63L;
    private static final String SOURCE_OBJECT_KEY = "imports/63/test/source.csv";
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-17T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TransactionImportProcessingLeaseManager processingLeaseManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS transaction_import_fence_effect (
                    id BIGINT PRIMARY KEY,
                    effect_value VARCHAR(100) NOT NULL
                )
                """);

        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    transaction_import_fence_effect,
                    transaction_import,
                    financial_account,
                    fintrack_user
                CASCADE
                """);

        jdbcTemplate.update("""
                INSERT INTO fintrack_user (
                    id,
                    currency
                )
                VALUES (?, ?)
                """,
                USER_ID,
                "USD");

        jdbcTemplate.update("""
                INSERT INTO financial_account (
                    id,
                    user_id,
                    current_balance,
                    status,
                    version,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, clock_timestamp(), clock_timestamp())
                """,
                ACCOUNT_ID,
                USER_ID,
                1000.00,
                "ACTIVE",
                0);

        jdbcTemplate.update("""
                INSERT INTO transaction_import (
                    id,
                    account_id,
                    original_file_name,
                    content_type,
                    file_size_bytes,
                    source_object_key,
                    status,
                    processed_rows,
                    successful_rows,
                    skipped_rows,
                    failed_rows,
                    processing_fencing_token,
                    version,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?,
                    0, 0, 0, 0, 0, 0,
                    clock_timestamp(),
                    clock_timestamp()
                )
                """,
                IMPORT_ID,
                ACCOUNT_ID,
                "transactions.csv",
                "text/csv",
                128L,
                SOURCE_OBJECT_KEY,
                "QUEUED");
    }

    @Test
    void staleWorkerCannotCommitChunkAfterExpiredLeaseIsTakenOver() throws Exception {
        TransactionImportProcessingAttempt firstAttempt =
                processingLeaseManager.acquire(createEvent(UUID.randomUUID()))
                        .getProcessingAttempt();

        TransactionImportChunkCommitFence firstWorkerFence = createFence(firstAttempt);

        CountDownLatch uncommittedEffectWritten = new CountDownLatch(1);
        CountDownLatch allowFirstWorkerToCheckFence = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> firstWorkerFuture = executor.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        jdbcTemplate.update("""
                                INSERT INTO transaction_import_fence_effect (
                                    id,
                                    effect_value
                                )
                                VALUES (?, ?)
                                """,
                                1L,
                                "uncommitted first-worker effect");

                        uncommittedEffectWritten.countDown();
                        await(allowFirstWorkerToCheckFence);

                        firstWorkerFence.update(new ExecutionContext());
                    })
            );

            try {
                assertThat(uncommittedEffectWritten.await(10, TimeUnit.SECONDS)).isTrue();

                jdbcTemplate.update("""
                        UPDATE transaction_import
                        SET processing_lease_expires_at =
                                clock_timestamp() - INTERVAL '1 second'
                        WHERE id = ?
                        """,
                        IMPORT_ID);

                TransactionImportProcessingLeaseAcquisition secondAcquisition =
                        processingLeaseManager.acquire(createEvent(UUID.randomUUID()));

                assertThat(secondAcquisition.getOutcome())
                        .isEqualTo(TransactionImportProcessingLeaseAcquisition.Outcome.ACQUIRED);

                TransactionImportProcessingAttempt secondAttempt =
                        secondAcquisition.getProcessingAttempt();

                assertThat(secondAttempt.getProcessingOwner())
                        .isNotEqualTo(firstAttempt.getProcessingOwner());
                assertThat(secondAttempt.getFencingToken())
                        .isEqualTo(firstAttempt.getFencingToken() + 1);

                allowFirstWorkerToCheckFence.countDown();

                assertThatThrownBy(() -> firstWorkerFuture.get(10, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(TransactionImportProcessingLeaseLostException.class);

                assertThat(readCommittedEffectCount()).isZero();

                assertThatCode(() -> assertActive(secondAttempt))
                        .doesNotThrowAnyException();
            } finally {
                allowFirstWorkerToCheckFence.countDown();
            }
        }
    }

    private TransactionImportChunkCommitFence createFence(
            TransactionImportProcessingAttempt processingAttempt) {

        return new TransactionImportChunkCommitFence(processingLeaseManager,
                processingAttempt.getImportId(),
                processingAttempt.getAccountId(),
                processingAttempt.getUserId(),
                processingAttempt.getProcessingOwner(),
                processingAttempt.getFencingToken());
    }

    private void assertActive(TransactionImportProcessingAttempt processingAttempt) {
        transactionTemplate.executeWithoutResult(status ->
                processingLeaseManager.assertActive(
                        processingAttempt.getImportId(),
                        processingAttempt.getAccountId(),
                        processingAttempt.getUserId(),
                        processingAttempt.getProcessingOwner(),
                        processingAttempt.getFencingToken()
                )
        );
    }

    private TransactionImportRequestedEvent createEvent(UUID eventId) {
        return TransactionImportRequestedEvent.create(eventId,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                SOURCE_OBJECT_KEY,
                eventId.toString(),
                OCCURRED_AT);
    }

    private int readCommittedEffectCount() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM transaction_import_fence_effect
                """,
                Integer.class);

        return count;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to continue stale worker");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to continue stale worker",
                    exception);
        }
    }
}