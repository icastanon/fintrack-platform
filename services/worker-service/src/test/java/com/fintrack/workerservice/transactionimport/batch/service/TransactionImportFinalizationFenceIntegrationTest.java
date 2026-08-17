package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.WorkerServiceApplication;
import com.fintrack.workerservice.idempotency.service.ProcessedMessageService;
import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportRejectedOutput;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportProcessingLeaseLostException;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingLeaseAcquisition;
import com.fintrack.workerservice.transactionimport.service.TransactionImportProcessingLeaseManager;
import com.fintrack.workerservice.transactionimport.service.TransactionImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
@Import({
        ProcessedMessageService.class,
        TransactionImportService.class,
        TransactionImportProcessingLeaseManager.class,
        TransactionImportJobFinalizationService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ContextConfiguration(classes = WorkerServiceApplication.class)
class TransactionImportFinalizationFenceIntegrationTest {

    private static final UUID EVENT_ID =
            UUID.fromString("8fb4e595-dbbc-4b7f-a791-8902bf5d93e1");

    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 52L;
    private static final Long USER_ID = 63L;
    private static final String SOURCE_OBJECT_KEY = "imports/63/finalization-fence/source.csv";
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-17T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionImportProcessingLeaseManager processingLeaseManager;

    @Autowired
    private TransactionImportJobFinalizationService jobFinalizationService;

    private TransactionImportRequestedEvent event;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                ALTER TABLE processed_message
                ALTER COLUMN processed_at SET DEFAULT CURRENT_TIMESTAMP
                """);

        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    processed_message,
                    financial_transaction,
                    transaction_import,
                    financial_account,
                    fintrack_user
                CASCADE
                """);

        insertUser();
        insertAccount();
        insertTransactionImport();

        event = createEvent();
    }

    @Test
    void staleWorkerCannotCompleteOrFailImportAfterLeaseTakeover() {
        TransactionImportProcessingAttempt firstAttempt = acquireLease();

        expireProcessingLease();

        TransactionImportProcessingAttempt secondAttempt = acquireLease();

        assertThat(secondAttempt.getProcessingOwner())
                .isNotEqualTo(firstAttempt.getProcessingOwner());

        assertThat(secondAttempt.getFencingToken())
                .isEqualTo(firstAttempt.getFencingToken() + 1);

        assertThatThrownBy(() -> jobFinalizationService.complete(
                event,
                firstAttempt,
                jobExecution(BatchStatus.COMPLETED),
                TransactionImportRejectedOutput.none()
        ))
                .isInstanceOf(TransactionImportProcessingLeaseLostException.class);

        assertThatThrownBy(() -> jobFinalizationService.fail(
                event,
                firstAttempt,
                jobExecution(BatchStatus.FAILED),
                "Failure reported by stale worker"
        ))
                .isInstanceOf(TransactionImportProcessingLeaseLostException.class);

        assertThat(readImportStatus()).isEqualTo("RUNNING");
        assertThat(readProcessingOwner()).isEqualTo(secondAttempt.getProcessingOwner());
        assertThat(readFencingToken()).isEqualTo(secondAttempt.getFencingToken());
        assertThat(readProcessedMessageCount()).isZero();
        assertThat(readFailureSummary()).isNull();
        assertThat(readCompletedAt()).isNull();

        boolean firstCompletion = jobFinalizationService.complete(
                event,
                secondAttempt,
                jobExecution(BatchStatus.COMPLETED),
                TransactionImportRejectedOutput.none()
        );

        assertThat(firstCompletion).isTrue();
        assertThat(readImportStatus()).isEqualTo("COMPLETED");
        assertThat(readProcessedMessageCount()).isEqualTo(1);
        assertThat(readFailureSummary()).isNull();
        assertThat(readCompletedAt()).isNotNull();
    }

    private TransactionImportProcessingAttempt acquireLease() {
        TransactionImportProcessingLeaseAcquisition acquisition =
                processingLeaseManager.acquire(event);

        assertThat(acquisition.getOutcome())
                .isEqualTo(TransactionImportProcessingLeaseAcquisition.Outcome.ACQUIRED);

        assertThat(acquisition.getProcessingAttempt()).isNotNull();

        return acquisition.getProcessingAttempt();
    }

    private void expireProcessingLease() {
        jdbcTemplate.update("""
                UPDATE transaction_import
                SET processing_lease_expires_at =
                        clock_timestamp() - INTERVAL '1 second'
                WHERE id = ?
                """,
                IMPORT_ID);
    }

    private JobExecution jobExecution(BatchStatus status) {
        JobInstance jobInstance = new JobInstance(71L, "transactionImportJob");

        JobExecution jobExecution = new JobExecution(
                81L,
                jobInstance,
                new JobParameters()
        );

        jobExecution.setStatus(status);

        StepExecution stepExecution = new StepExecution(
                91L,
                "transactionImportStep",
                jobExecution
        );

        stepExecution.setProcessSkipCount(0L);
        jobExecution.addStepExecution(stepExecution);

        return jobExecution;
    }

    private TransactionImportRequestedEvent createEvent() {
        return TransactionImportRequestedEvent.create(
                EVENT_ID,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                SOURCE_OBJECT_KEY,
                EVENT_ID.toString(),
                OCCURRED_AT
        );
    }

    private void insertUser() {
        jdbcTemplate.update("""
                INSERT INTO fintrack_user (
                    id,
                    currency
                )
                VALUES (?, ?)
                """,
                USER_ID,
                "USD");
    }

    private void insertAccount() {
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

    private void insertTransactionImport() {
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

    private String readImportStatus() {
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

    private Long readFencingToken() {
        return jdbcTemplate.queryForObject("""
                SELECT processing_fencing_token
                FROM transaction_import
                WHERE id = ?
                """,
                Long.class,
                IMPORT_ID);
    }

    private long readProcessedMessageCount() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM processed_message
                WHERE event_id = ?
                """,
                Long.class,
                EVENT_ID);

        return count == null ? 0 : count;
    }

    private String readFailureSummary() {
        return jdbcTemplate.queryForObject("""
                SELECT failure_summary
                FROM transaction_import
                WHERE id = ?
                """,
                String.class,
                IMPORT_ID);
    }

    private Instant readCompletedAt() {
        return jdbcTemplate.queryForObject("""
                SELECT completed_at
                FROM transaction_import
                WHERE id = ?
                """,
                Instant.class,
                IMPORT_ID);
    }
}