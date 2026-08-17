package com.fintrack.workerservice.transactionimport.listener;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.WorkerServiceApplication;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingLeaseAcquisition;
import com.fintrack.workerservice.transactionimport.service.TransactionImportProcessingLeaseManager;
import io.awspring.cloud.sqs.listener.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
@ContextConfiguration(classes = WorkerServiceApplication.class)
class TransactionImportMessageVisibilityHeartbeatIntegrationTest {

    private static final UUID EVENT_ID =
            UUID.fromString("8fb4e595-dbbc-4b7f-a791-8902bf5d93e1");

    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 52L;
    private static final Long USER_ID = 63L;
    private static final String SOURCE_OBJECT_KEY = "imports/63/heartbeat/source.csv";
    private static final int VISIBILITY_EXTENSION_SECONDS = 120;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionImportProcessingLeaseManager processingLeaseManager;

    private TaskScheduler taskScheduler;
    private Visibility visibility;
    private ScheduledFuture<?> scheduledTask;
    private TransactionImportMessageVisibilityHeartbeat heartbeat;
    private TransactionImportRequestedEvent event;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    transaction_import,
                    financial_account,
                    fintrack_user
                CASCADE
                """);

        insertUser();
        insertAccount();
        insertTransactionImport();

        taskScheduler = mock(TaskScheduler.class);
        visibility = mock(Visibility.class);
        scheduledTask = mock(ScheduledFuture.class);

        heartbeat = new TransactionImportMessageVisibilityHeartbeat(
                taskScheduler,
                processingLeaseManager,
                VISIBILITY_EXTENSION_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS
        );

        event = createEvent();
    }

    @Test
    void heartbeatStopsExtendingVisibilityAfterAnotherWorkerTakesOverLease() {
        TransactionImportProcessingAttempt firstAttempt = acquireLease();

        ArgumentCaptor<Runnable> heartbeatTaskCaptor = arrangeScheduledTask();

        TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat =
                heartbeat.start(visibility, firstAttempt);

        verify(visibility).changeTo(VISIBILITY_EXTENSION_SECONDS);
        assertThat(runningHeartbeat.hasLostProcessingLease()).isFalse();

        expireProcessingLease();

        TransactionImportProcessingAttempt secondAttempt = acquireLease();

        assertThat(secondAttempt.getProcessingOwner())
                .isNotEqualTo(firstAttempt.getProcessingOwner());

        assertThat(secondAttempt.getFencingToken())
                .isEqualTo(firstAttempt.getFencingToken() + 1);

        heartbeatTaskCaptor.getValue().run();
        heartbeatTaskCaptor.getValue().run();

        assertThat(runningHeartbeat.hasLostProcessingLease()).isTrue();

        verify(visibility, times(1)).changeTo(VISIBILITY_EXTENSION_SECONDS);

        runningHeartbeat.close();

        verify(scheduledTask).cancel(false);

        assertThat(readProcessingOwner()).isEqualTo(secondAttempt.getProcessingOwner());
        assertThat(readFencingToken()).isEqualTo(secondAttempt.getFencingToken());
    }

    private ArgumentCaptor<Runnable> arrangeScheduledTask() {
        when(taskScheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        doReturn(scheduledTask).when(taskScheduler).scheduleAtFixedRate(
                taskCaptor.capture(),
                any(Instant.class),
                eq(Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS))
        );

        return taskCaptor;
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

    private TransactionImportRequestedEvent createEvent() {
        return TransactionImportRequestedEvent.create(
                EVENT_ID,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                SOURCE_OBJECT_KEY,
                EVENT_ID.toString(),
                NOW
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
}