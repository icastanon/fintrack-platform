package com.fintrack.workerservice.transactionimport.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportProcessingLeaseLostException;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingLeaseAcquisition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
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
class TransactionImportProcessingLeaseManagerIntegrationTest {

    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 52L;
    private static final Long USER_ID = 63L;
    private static final String SOURCE_OBJECT_KEY = "imports/63/test/source.csv";
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-17T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

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
                TRUNCATE TABLE transaction_import, financial_account, fintrack_user CASCADE
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
    void acquireClaimsLeaseAndMarksImportRunning() {
        TransactionImportProcessingLeaseAcquisition acquisition =
                processingLeaseManager.acquire(createEvent(UUID.randomUUID()));

        assertThat(acquisition.getOutcome())
                .isEqualTo(TransactionImportProcessingLeaseAcquisition.Outcome.ACQUIRED);

        TransactionImportProcessingAttempt processingAttempt = acquisition.getProcessingAttempt();

        assertThat(processingAttempt).isNotNull();
        assertThat(processingAttempt.getImportId()).isEqualTo(IMPORT_ID);
        assertThat(processingAttempt.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(processingAttempt.getUserId()).isEqualTo(USER_ID);
        assertThat(processingAttempt.getProcessingOwner()).isNotBlank();
        assertThat(processingAttempt.getFencingToken()).isEqualTo(1L);

        assertThat(readStatus()).isEqualTo("RUNNING");
        assertThat(readProcessingOwner()).isEqualTo(processingAttempt.getProcessingOwner());
        assertThat(readFencingToken()).isEqualTo(1L);
        assertThat(hasFutureLeaseExpiration()).isTrue();
    }

    @Test
    void acquireReturnsActiveLeaseWhileCurrentLeaseHasNotExpired() {
        TransactionImportProcessingAttempt firstAttempt =
                processingLeaseManager.acquire(createEvent(UUID.randomUUID())).getProcessingAttempt();

        TransactionImportProcessingLeaseAcquisition secondAcquisition =
                processingLeaseManager.acquire(createEvent(UUID.randomUUID()));

        assertThat(secondAcquisition.getOutcome())
                .isEqualTo(TransactionImportProcessingLeaseAcquisition.Outcome.ACTIVE_LEASE);
        assertThat(secondAcquisition.getProcessingAttempt()).isNull();

        assertThat(readProcessingOwner()).isEqualTo(firstAttempt.getProcessingOwner());
        assertThat(readFencingToken()).isEqualTo(firstAttempt.getFencingToken());
    }

    @Test
    void concurrentAcquireAllowsExactlyOneLeaseOwner() throws Exception {
        CyclicBarrier startBarrier = new CyclicBarrier(2);

        TransactionImportProcessingLeaseAcquisition firstAcquisition;
        TransactionImportProcessingLeaseAcquisition secondAcquisition;

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<TransactionImportProcessingLeaseAcquisition> firstFuture = executor.submit(
                    () -> acquireAfterBarrier(startBarrier, UUID.randomUUID())
            );

            Future<TransactionImportProcessingLeaseAcquisition> secondFuture = executor.submit(
                    () -> acquireAfterBarrier(startBarrier, UUID.randomUUID())
            );

            firstAcquisition = firstFuture.get(10, TimeUnit.SECONDS);
            secondAcquisition = secondFuture.get(10, TimeUnit.SECONDS);
        }

        List<TransactionImportProcessingLeaseAcquisition> acquisitions =
                List.of(firstAcquisition, secondAcquisition);

        assertThat(acquisitions)
                .extracting(TransactionImportProcessingLeaseAcquisition::getOutcome)
                .containsExactlyInAnyOrder(
                        TransactionImportProcessingLeaseAcquisition.Outcome.ACQUIRED,
                        TransactionImportProcessingLeaseAcquisition.Outcome.ACTIVE_LEASE
                );

        TransactionImportProcessingAttempt winningAttempt = acquisitions.stream()
                .filter(TransactionImportProcessingLeaseAcquisition::isAcquired)
                .map(TransactionImportProcessingLeaseAcquisition::getProcessingAttempt)
                .findFirst()
                .orElseThrow();

        assertThat(winningAttempt.getFencingToken()).isEqualTo(1L);
        assertThat(readProcessingOwner()).isEqualTo(winningAttempt.getProcessingOwner());
        assertThat(readFencingToken()).isEqualTo(winningAttempt.getFencingToken());
        assertThat(hasFutureLeaseExpiration()).isTrue();
    }

    @Test
    void renewExtendsLeaseForCurrentOwnerAndFencingToken() {
        TransactionImportProcessingAttempt processingAttempt =
                processingLeaseManager.acquire(createEvent(UUID.randomUUID())).getProcessingAttempt();

        jdbcTemplate.update("""
                UPDATE transaction_import
                SET processing_lease_expires_at = clock_timestamp() + INTERVAL '5 seconds'
                WHERE id = ?
                """,
                IMPORT_ID);

        boolean renewed = processingLeaseManager.renew(processingAttempt);

        assertThat(renewed).isTrue();
        assertThat(hasLeaseWithMoreThanTwentySecondsRemaining()).isTrue();
        assertThat(readProcessingOwner()).isEqualTo(processingAttempt.getProcessingOwner());
        assertThat(readFencingToken()).isEqualTo(processingAttempt.getFencingToken());
    }

    @Test
    void expiredLeaseCanBeTakenOverAndOldAttemptIsFenced() {
        TransactionImportProcessingAttempt firstAttempt =
                processingLeaseManager.acquire(createEvent(UUID.randomUUID())).getProcessingAttempt();

        jdbcTemplate.update("""
                UPDATE transaction_import
                SET processing_lease_expires_at = clock_timestamp() - INTERVAL '1 second'
                WHERE id = ?
                """,
                IMPORT_ID);

        TransactionImportProcessingLeaseAcquisition secondAcquisition =
                processingLeaseManager.acquire(createEvent(UUID.randomUUID()));

        assertThat(secondAcquisition.getOutcome())
                .isEqualTo(TransactionImportProcessingLeaseAcquisition.Outcome.ACQUIRED);

        TransactionImportProcessingAttempt secondAttempt = secondAcquisition.getProcessingAttempt();

        assertThat(secondAttempt.getProcessingOwner()).isNotEqualTo(firstAttempt.getProcessingOwner());
        assertThat(secondAttempt.getFencingToken()).isEqualTo(firstAttempt.getFencingToken() + 1);

        assertThat(processingLeaseManager.renew(firstAttempt)).isFalse();
        assertThat(processingLeaseManager.release(firstAttempt)).isFalse();

        assertThatThrownBy(() -> assertActive(firstAttempt))
                .isInstanceOf(TransactionImportProcessingLeaseLostException.class);

        assertThatCode(() -> assertActive(secondAttempt)).doesNotThrowAnyException();

        assertThat(readProcessingOwner()).isEqualTo(secondAttempt.getProcessingOwner());
        assertThat(readFencingToken()).isEqualTo(secondAttempt.getFencingToken());
    }

    @Test
    void currentOwnerCanReleaseLeaseWithoutResettingFencingToken() {
        TransactionImportProcessingAttempt processingAttempt =
                processingLeaseManager.acquire(createEvent(UUID.randomUUID())).getProcessingAttempt();

        boolean released = processingLeaseManager.release(processingAttempt);

        assertThat(released).isTrue();
        assertThat(readProcessingOwner()).isNull();
        assertThat(hasLeaseExpiration()).isFalse();
        assertThat(readFencingToken()).isEqualTo(processingAttempt.getFencingToken());
        assertThat(processingLeaseManager.renew(processingAttempt)).isFalse();
    }

    @Test
    void acquireRecognizesAlreadyCompletedImport() {
        jdbcTemplate.update("""
                UPDATE transaction_import
                SET status = 'COMPLETED',
                    completed_at = clock_timestamp()
                WHERE id = ?
                """,
                IMPORT_ID);

        TransactionImportProcessingLeaseAcquisition acquisition =
                processingLeaseManager.acquire(createEvent(UUID.randomUUID()));

        assertThat(acquisition.getOutcome())
                .isEqualTo(TransactionImportProcessingLeaseAcquisition.Outcome.ALREADY_COMPLETED);
        assertThat(acquisition.getProcessingAttempt()).isNull();
        assertThat(readProcessingOwner()).isNull();
        assertThat(readFencingToken()).isZero();
    }

    private TransactionImportProcessingLeaseAcquisition acquireAfterBarrier(
            CyclicBarrier startBarrier,
            UUID eventId) throws Exception {
        startBarrier.await(5, TimeUnit.SECONDS);
        return processingLeaseManager.acquire(createEvent(eventId));
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

    private String readStatus() {
        return jdbcTemplate.queryForObject("""
                SELECT status
                FROM transaction_import
                WHERE id = ?
                """,
                String.class,
                IMPORT_ID);
    }

    private String readProcessingOwner() {
        return jdbcTemplate.queryForObject("""
                SELECT processing_owner
                FROM transaction_import
                WHERE id = ?
                """,
                String.class,
                IMPORT_ID);
    }

    private long readFencingToken() {
        Long fencingToken = jdbcTemplate.queryForObject("""
                SELECT processing_fencing_token
                FROM transaction_import
                WHERE id = ?
                """,
                Long.class,
                IMPORT_ID);

        return fencingToken;
    }

    private boolean hasFutureLeaseExpiration() {
        Boolean result = jdbcTemplate.queryForObject("""
                SELECT processing_lease_expires_at > clock_timestamp()
                FROM transaction_import
                WHERE id = ?
                """,
                Boolean.class,
                IMPORT_ID);

        return Boolean.TRUE.equals(result);
    }

    private boolean hasLeaseWithMoreThanTwentySecondsRemaining() {
        Boolean result = jdbcTemplate.queryForObject("""
                SELECT processing_lease_expires_at > clock_timestamp() + INTERVAL '20 seconds'
                FROM transaction_import
                WHERE id = ?
                """,
                Boolean.class,
                IMPORT_ID);

        return Boolean.TRUE.equals(result);
    }

    private boolean hasLeaseExpiration() {
        Boolean result = jdbcTemplate.queryForObject("""
                SELECT processing_lease_expires_at IS NOT NULL
                FROM transaction_import
                WHERE id = ?
                """,
                Boolean.class,
                IMPORT_ID);

        return Boolean.TRUE.equals(result);
    }
}