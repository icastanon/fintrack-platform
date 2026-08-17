package com.fintrack.workerservice.transactionimport.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest(
        showSql = false,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop"
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionImportRejectedRowStagingRepositoryIntegrationTest {

    private static final Long USER_ID = 63L;
    private static final Long ACCOUNT_ID = 52L;

    private static final Long OLD_COMPLETED_IMPORT_ID = 41L;
    private static final Long RECENT_COMPLETED_IMPORT_ID = 42L;
    private static final Long OLD_FAILED_IMPORT_ID = 43L;
    private static final Long RUNNING_IMPORT_ID = 44L;

    private static final Instant COMPLETED_BEFORE = Instant.parse("2026-08-16T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionImportRejectedRowStagingRepository rejectedRowStagingRepository;

    @BeforeEach
    void setUp() {
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

        insertImport(
                OLD_COMPLETED_IMPORT_ID,
                "COMPLETED",
                COMPLETED_BEFORE.minusSeconds(1)
        );

        insertImport(
                RECENT_COMPLETED_IMPORT_ID,
                "COMPLETED",
                COMPLETED_BEFORE.plusSeconds(1)
        );

        insertImport(
                OLD_FAILED_IMPORT_ID,
                "FAILED",
                COMPLETED_BEFORE.minusSeconds(1)
        );

        insertImport(
                RUNNING_IMPORT_ID,
                "RUNNING",
                null
        );

        insertRejectedRow(OLD_COMPLETED_IMPORT_ID);
        insertRejectedRow(RECENT_COMPLETED_IMPORT_ID);
        insertRejectedRow(OLD_FAILED_IMPORT_ID);
        insertRejectedRow(RUNNING_IMPORT_ID);
    }

    @Test
    void deleteAllForCompletedImportsBeforeDeletesOnlyOldCompletedImportRows() {
        int deletedRows =
                rejectedRowStagingRepository.deleteAllForCompletedImportsBefore(COMPLETED_BEFORE);

        assertThat(deletedRows).isEqualTo(1);

        assertThat(rejectedRowStagingRepository.countByImportId(OLD_COMPLETED_IMPORT_ID))
                .isZero();

        assertThat(rejectedRowStagingRepository.countByImportId(RECENT_COMPLETED_IMPORT_ID))
                .isEqualTo(1);

        assertThat(rejectedRowStagingRepository.countByImportId(OLD_FAILED_IMPORT_ID))
                .isEqualTo(1);

        assertThat(rejectedRowStagingRepository.countByImportId(RUNNING_IMPORT_ID))
                .isEqualTo(1);
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
}