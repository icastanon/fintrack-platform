package com.fintrack.workerservice.transactionimport.service;

import com.fintrack.workerservice.transactionimport.model.TransactionImportAbandonmentResult;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest(
        showSql = false,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop"
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TransactionImportRetentionService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransactionImportRetentionServiceIntegrationTest {

    private static final Long USER_ID = 63L;
    private static final Long ACCOUNT_ID = 52L;
    private static final Instant FAILED_BEFORE = Instant.parse("2026-08-01T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TransactionImportRetentionService retentionService;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);

        jdbcTemplate.execute("""
                DROP TRIGGER IF EXISTS reject_staging_delete
                ON transaction_import_rejected_row_staging
                """);

        jdbcTemplate.execute("""
                DROP FUNCTION IF EXISTS fail_transaction_import_staging_delete()
                """);

        jdbcTemplate.execute("""
                TRUNCATE TABLE transaction_import_rejected_row_staging,
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
    }

    @Test
    void abandonmentSelectsOnlyEligibleFailedImportsAndDeletesTheirStaging() {
        insertImport(41L, "FAILED", FAILED_BEFORE.minusSeconds(1));
        insertImport(42L, "FAILED", FAILED_BEFORE.minusSeconds(2));
        insertImport(43L, "FAILED", FAILED_BEFORE.plusSeconds(1));
        insertImport(44L, "FAILED", FAILED_BEFORE.minusSeconds(3));
        insertImport(45L, "COMPLETED", FAILED_BEFORE.minusSeconds(4));
        insertImport(46L, "RUNNING", null);

        setExpiredLease(42L, 4L);
        setActiveLease(44L, 5L);

        insertRejectedRow(41L);
        insertRejectedRow(42L);
        insertRejectedRow(43L);
        insertRejectedRow(44L);
        insertRejectedRow(45L);
        insertRejectedRow(46L);

        TransactionImportAbandonmentResult result =
                retentionService.abandonStaleFailedImports(FAILED_BEFORE, 100);

        assertThat(result.getAbandonedImportCount()).isEqualTo(2);
        assertThat(result.getDeletedRejectedRowCount()).isEqualTo(2);

        assertThat(readStatus(41L)).isEqualTo("ABANDONED");
        assertThat(readStatus(42L)).isEqualTo("ABANDONED");
        assertThat(readStatus(43L)).isEqualTo("FAILED");
        assertThat(readStatus(44L)).isEqualTo("FAILED");
        assertThat(readStatus(45L)).isEqualTo("COMPLETED");
        assertThat(readStatus(46L)).isEqualTo("RUNNING");

        assertThat(countStagingRows(41L)).isZero();
        assertThat(countStagingRows(42L)).isZero();
        assertThat(countStagingRows(43L)).isEqualTo(1);
        assertThat(countStagingRows(44L)).isEqualTo(1);
        assertThat(countStagingRows(45L)).isEqualTo(1);
        assertThat(countStagingRows(46L)).isEqualTo(1);

        assertThat(readProcessingOwner(42L)).isNull();
        assertThat(readFencingToken(42L)).isEqualTo(4L);
        assertThat(readProcessingOwner(44L)).isEqualTo("active-worker");
        assertThat(readFencingToken(44L)).isEqualTo(5L);
    }

    @Test
    void abandonmentHonorsBatchSizeAndProcessesOldestFailuresFirst() {
        insertImport(51L, "FAILED", FAILED_BEFORE.minusSeconds(300));
        insertImport(52L, "FAILED", FAILED_BEFORE.minusSeconds(200));
        insertImport(53L, "FAILED", FAILED_BEFORE.minusSeconds(100));

        insertRejectedRow(51L);
        insertRejectedRow(52L);
        insertRejectedRow(53L);

        TransactionImportAbandonmentResult firstResult =
                retentionService.abandonStaleFailedImports(FAILED_BEFORE, 2);

        assertThat(firstResult.getAbandonedImportCount()).isEqualTo(2);
        assertThat(firstResult.getDeletedRejectedRowCount()).isEqualTo(2);
        assertThat(readStatus(51L)).isEqualTo("ABANDONED");
        assertThat(readStatus(52L)).isEqualTo("ABANDONED");
        assertThat(readStatus(53L)).isEqualTo("FAILED");

        TransactionImportAbandonmentResult secondResult =
                retentionService.abandonStaleFailedImports(FAILED_BEFORE, 2);

        assertThat(secondResult.getAbandonedImportCount()).isEqualTo(1);
        assertThat(secondResult.getDeletedRejectedRowCount()).isEqualTo(1);
        assertThat(readStatus(53L)).isEqualTo("ABANDONED");
    }

    @Test
    void abandonmentRollsBackStatusWhenStagingDeletionFails() {
        insertImport(61L, "FAILED", FAILED_BEFORE.minusSeconds(1));
        insertRejectedRow(61L);

        jdbcTemplate.execute("""
                CREATE FUNCTION fail_transaction_import_staging_delete()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $$
                BEGIN
                    RAISE EXCEPTION 'forced staging deletion failure';
                END;
                $$
                """);

        jdbcTemplate.execute("""
                CREATE TRIGGER reject_staging_delete
                BEFORE DELETE ON transaction_import_rejected_row_staging
                FOR EACH STATEMENT
                EXECUTE FUNCTION fail_transaction_import_staging_delete()
                """);

        try {
            assertThatThrownBy(() ->
                    retentionService.abandonStaleFailedImports(FAILED_BEFORE, 100))
                    .isInstanceOf(DataAccessException.class);

            assertThat(readStatus(61L)).isEqualTo("FAILED");
            assertThat(countStagingRows(61L)).isEqualTo(1);
        } finally {
            jdbcTemplate.execute("""
                    DROP TRIGGER IF EXISTS reject_staging_delete
                    ON transaction_import_rejected_row_staging
                    """);

            jdbcTemplate.execute("""
                    DROP FUNCTION IF EXISTS fail_transaction_import_staging_delete()
                    """);
        }
    }

    @Test
    void abandonmentSkipsImportLockedByAnotherCleanupTransaction() throws Exception {
        insertImport(71L, "FAILED", FAILED_BEFORE.minusSeconds(2));
        insertImport(72L, "FAILED", FAILED_BEFORE.minusSeconds(1));

        insertRejectedRow(71L);
        insertRejectedRow(72L);

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> lockFuture = executor.submit(() ->
                    holdImportLock(71L, lockAcquired, releaseLock));

            assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            TransactionImportAbandonmentResult result;

            try {
                result = retentionService.abandonStaleFailedImports(FAILED_BEFORE, 100);
            } finally {
                releaseLock.countDown();
            }

            lockFuture.get(10, TimeUnit.SECONDS);

            assertThat(result.getAbandonedImportCount()).isEqualTo(1);
            assertThat(result.getDeletedRejectedRowCount()).isEqualTo(1);
            assertThat(readStatus(71L)).isEqualTo("FAILED");
            assertThat(readStatus(72L)).isEqualTo("ABANDONED");
            assertThat(countStagingRows(71L)).isEqualTo(1);
            assertThat(countStagingRows(72L)).isZero();
        }
    }

    private void holdImportLock(Long importId,
                                CountDownLatch lockAcquired,
                                CountDownLatch releaseLock) {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject("""
                    SELECT id
                    FROM transaction_import
                    WHERE id = ?
                    FOR UPDATE
                    """,
                    Long.class,
                    importId);

            lockAcquired.countDown();
            awaitRelease(releaseLock);
        });
    }

    private void awaitRelease(CountDownLatch releaseLock) {
        try {
            if (!releaseLock.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release transaction-import lock");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding transaction-import lock", exception);
        }
    }

    private void insertImport(Long importId, String status, Instant completedAt) {
        Timestamp completedTimestamp = completedAt == null ? null : Timestamp.from(completedAt);

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
                    completed_at,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?,
                    0, 0, 0, 0, 0, 0, ?,
                    clock_timestamp(),
                    clock_timestamp()
                )
                """,
                importId,
                ACCOUNT_ID,
                "transactions-" + importId + ".csv",
                "text/csv",
                128L,
                "imports/" + USER_ID + "/" + importId + "/source.csv",
                status,
                completedTimestamp);
    }

    private void insertRejectedRow(Long importId) {
        jdbcTemplate.update("""
                INSERT INTO transaction_import_rejected_row_staging (
                    import_id,
                    row_number,
                    raw_record,
                    failure_reason,
                    created_at
                )
                VALUES (?, ?, ?, ?, clock_timestamp())
                """,
                importId,
                2,
                "2026-08-10,EXPENSE,invalid,STARBUCKS,Coffee",
                "Row 2: amount must be a valid decimal number");
    }

    private void setExpiredLease(Long importId, long fencingToken) {
        jdbcTemplate.update("""
                UPDATE transaction_import
                SET processing_owner = 'expired-worker',
                    processing_lease_expires_at = clock_timestamp() - INTERVAL '1 second',
                    processing_fencing_token = ?
                WHERE id = ?
                """,
                fencingToken,
                importId);
    }

    private void setActiveLease(Long importId, long fencingToken) {
        jdbcTemplate.update("""
                UPDATE transaction_import
                SET processing_owner = 'active-worker',
                    processing_lease_expires_at = clock_timestamp() + INTERVAL '1 hour',
                    processing_fencing_token = ?
                WHERE id = ?
                """,
                fencingToken,
                importId);
    }

    private String readStatus(Long importId) {
        return jdbcTemplate.queryForObject("""
                SELECT status
                FROM transaction_import
                WHERE id = ?
                """,
                String.class,
                importId);
    }

    private String readProcessingOwner(Long importId) {
        return jdbcTemplate.queryForObject("""
                SELECT processing_owner
                FROM transaction_import
                WHERE id = ?
                """,
                String.class,
                importId);
    }

    private long readFencingToken(Long importId) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT processing_fencing_token
                FROM transaction_import
                WHERE id = ?
                """,
                Long.class,
                importId);

        return value;
    }

    private long countStagingRows(Long importId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM transaction_import_rejected_row_staging
                WHERE import_id = ?
                """,
                Long.class,
                importId);

        return count;
    }
}