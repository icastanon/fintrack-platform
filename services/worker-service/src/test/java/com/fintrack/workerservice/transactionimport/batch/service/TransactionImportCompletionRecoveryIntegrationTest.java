package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.WorkerServiceApplication;
import com.fintrack.workerservice.idempotency.service.ProcessedMessageService;
import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportRejectedOutput;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import com.fintrack.workerservice.transactionimport.service.TransactionImportProcessingLeaseManager;
import com.fintrack.workerservice.transactionimport.service.TransactionImportService;
import com.fintrack.workerservice.transactionimport.storage.TransactionImportStorageService;
import org.junit.jupiter.api.AfterEach;
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
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.charset.StandardCharsets;
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
class TransactionImportCompletionRecoveryIntegrationTest {

    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 52L;
    private static final Long USER_ID = 63L;
    private static final String IMPORT_BUCKET = "fintrack-imports";
    private static final String SOURCE_OBJECT_KEY = "imports/63/recovery/source.csv";
    private static final String REJECTED_OBJECT_KEY = "imports/63/recovery/rejected.csv";
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-17T12:00:00Z");
    private static final String LOCALSTACK_AUTH_TOKEN = requireLocalStackAuthToken();

    private static final byte[] FIRST_ARTIFACT = """
            row_number,raw_record,failure_reason
            2,"invalid row","amount must be valid"
            """.getBytes(StandardCharsets.UTF_8);

    private static final byte[] RETRY_ARTIFACT = """
            row_number,raw_record,failure_reason
            2,"invalid row","amount must be valid"
            4,"another invalid row","transaction type must be valid"
            """.getBytes(StandardCharsets.UTF_8);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer("localstack/localstack:2026.07.2")
                    .withEnv("LOCALSTACK_AUTH_TOKEN", LOCALSTACK_AUTH_TOKEN)
                    .withServices("s3");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionImportProcessingLeaseManager processingLeaseManager;

    @Autowired
    private TransactionImportJobFinalizationService jobFinalizationService;

    private S3Client s3Client;
    private TransactionImportStorageService storageService;
    private TransactionImportRequestedEvent event;
    private TransactionImportProcessingAttempt processingAttempt;

    @BeforeEach
    void setUp() {
        s3Client = S3Client.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                LOCALSTACK.getAccessKey(),
                                LOCALSTACK.getSecretKey()
                        )
                ))
                .region(Region.of(LOCALSTACK.getRegion()))
                .forcePathStyle(true)
                .build();

        s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(IMPORT_BUCKET)
                .build());

        storageService = new TransactionImportStorageService(s3Client, IMPORT_BUCKET);

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
        processingAttempt = processingLeaseManager.acquire(event).getProcessingAttempt();
    }

    @AfterEach
    void tearDown() {
        s3Client.close();
    }

    @Test
    void retryOverwritesRejectedArtifactAndCompletesAfterDatabaseFinalizationFailure() {
        String firstObjectKey =
                storageService.uploadRejectedOutput(SOURCE_OBJECT_KEY, FIRST_ARTIFACT);

        JobExecution mismatchedExecution = completedExecution(2);

        TransactionImportRejectedOutput mismatchedOutput =
                TransactionImportRejectedOutput.uploaded(1, firstObjectKey);

        assertThatThrownBy(() -> jobFinalizationService.complete(
                event,
                processingAttempt,
                mismatchedExecution,
                mismatchedOutput
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Spring Batch skip count does not match durable rejected-row count"
                );

        assertThat(firstObjectKey).isEqualTo(REJECTED_OBJECT_KEY);
        assertThat(readProcessedMessageCount()).isZero();
        assertThat(readImportStatus()).isEqualTo("RUNNING");
        assertThat(readRejectedObjectKey()).isNull();

        String retryObjectKey =
                storageService.uploadRejectedOutput(SOURCE_OBJECT_KEY, RETRY_ARTIFACT);

        JobExecution retryExecution = completedExecution(2);

        TransactionImportRejectedOutput retryOutput =
                TransactionImportRejectedOutput.uploaded(2, retryObjectKey);

        boolean firstCompletion = jobFinalizationService.complete(
                event,
                processingAttempt,
                retryExecution,
                retryOutput
        );

        assertThat(firstCompletion).isTrue();
        assertThat(retryObjectKey).isEqualTo(firstObjectKey);
        assertThat(downloadRejectedArtifact()).isEqualTo(RETRY_ARTIFACT);

        assertThat(readProcessedMessageCount()).isEqualTo(1);
        assertThat(readImportStatus()).isEqualTo("COMPLETED");
        assertThat(readSuccessfulRows()).isZero();
        assertThat(readSkippedRows()).isEqualTo(2);
        assertThat(readRejectedObjectKey()).isEqualTo(REJECTED_OBJECT_KEY);
    }

    private JobExecution completedExecution(long skippedRows) {
        JobInstance jobInstance = new JobInstance(71L, "transactionImportJob");
        JobExecution jobExecution = new JobExecution(
                81L,
                jobInstance,
                new JobParameters()
        );

        jobExecution.setStatus(BatchStatus.COMPLETED);

        StepExecution stepExecution = new StepExecution(
                91L,
                "transactionImportStep",
                jobExecution
        );

        stepExecution.setProcessSkipCount(skippedRows);
        jobExecution.addStepExecution(stepExecution);

        return jobExecution;
    }

    private byte[] downloadRejectedArtifact() {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(IMPORT_BUCKET)
                .key(REJECTED_OBJECT_KEY)
                .build();

        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
        return response.asByteArray();
    }

    private TransactionImportRequestedEvent createEvent() {
        UUID eventId = UUID.fromString("8fb4e595-dbbc-4b7f-a791-8902bf5d93e1");

        return TransactionImportRequestedEvent.create(eventId,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                SOURCE_OBJECT_KEY,
                eventId.toString(),
                OCCURRED_AT);
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

    private long readProcessedMessageCount() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM processed_message
                WHERE event_id = ?
                """,
                Long.class,
                event.getEventId());

        return count;
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

    private Long readSuccessfulRows() {
        return jdbcTemplate.queryForObject("""
                SELECT successful_rows
                FROM transaction_import
                WHERE id = ?
                """,
                Long.class,
                IMPORT_ID);
    }

    private Long readSkippedRows() {
        return jdbcTemplate.queryForObject("""
                SELECT skipped_rows
                FROM transaction_import
                WHERE id = ?
                """,
                Long.class,
                IMPORT_ID);
    }

    private String readRejectedObjectKey() {
        return jdbcTemplate.queryForObject("""
                SELECT rejected_object_key
                FROM transaction_import
                WHERE id = ?
                """,
                String.class,
                IMPORT_ID);
    }

    private static String requireLocalStackAuthToken() {
        String authToken = System.getenv("LOCALSTACK_AUTH_TOKEN");

        if (authToken == null || authToken.isBlank()) {
            throw new IllegalStateException(
                    "LOCALSTACK_AUTH_TOKEN must be configured for LocalStack integration tests"
            );
        }

        return authToken;
    }
}