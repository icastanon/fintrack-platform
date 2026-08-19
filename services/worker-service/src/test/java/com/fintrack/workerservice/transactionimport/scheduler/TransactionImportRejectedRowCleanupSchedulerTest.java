package com.fintrack.workerservice.transactionimport.scheduler;

import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportRejectedRowStagingService;
import com.fintrack.workerservice.transactionimport.metrics.TransactionImportRetentionMetrics;
import com.fintrack.workerservice.transactionimport.model.TransactionImportAbandonmentResult;
import com.fintrack.workerservice.transactionimport.service.TransactionImportRetentionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportRejectedRowCleanupSchedulerTest {

    private static final Duration RETENTION_DURATION = Duration.ofDays(1);
    private static final Duration FAILED_IMPORT_RECOVERY_WINDOW = Duration.ofDays(30);
    private static final int ABANDONMENT_BATCH_SIZE = 100;

    @Mock
    private TransactionImportRetentionService retentionService;

    @Mock
    private TransactionImportRetentionMetrics retentionMetrics;

    @Mock
    private TransactionImportRejectedRowStagingService rejectedRowStagingService;

    @Test
    void cleanupCompletedImportStagingDeletesRowsOlderThanRetentionDuration() {
        TransactionImportRejectedRowCleanupScheduler scheduler = scheduler();

        when(rejectedRowStagingService.deleteAllForCompletedImportsBefore(
                any(Instant.class)))
                .thenReturn(4);

        Instant invocationStartedAt = Instant.now();

        scheduler.cleanupCompletedImportStaging();

        Instant invocationFinishedAt = Instant.now();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(rejectedRowStagingService)
                .deleteAllForCompletedImportsBefore(cutoffCaptor.capture());

        assertThat(cutoffCaptor.getValue()).isBetween(
                invocationStartedAt.minus(RETENTION_DURATION),
                invocationFinishedAt.minus(RETENTION_DURATION)
        );
    }

    @Test
    void constructorRejectsMissingRetentionDuration() {
        assertThatThrownBy(() -> new TransactionImportRejectedRowCleanupScheduler(
                rejectedRowStagingService,
                retentionService,
                retentionMetrics,
                null,
                FAILED_IMPORT_RECOVERY_WINDOW,
                ABANDONMENT_BATCH_SIZE
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Rejected-row staging retention duration is required");

        verifyNoInteractions(rejectedRowStagingService);
    }

    @Test
    void constructorRejectsZeroRetentionDuration() {
        assertThatThrownBy(() -> new TransactionImportRejectedRowCleanupScheduler(
                rejectedRowStagingService,
                retentionService,
                retentionMetrics,
                Duration.ZERO,
                FAILED_IMPORT_RECOVERY_WINDOW,
                ABANDONMENT_BATCH_SIZE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rejected-row staging retention duration must be positive");

        verifyNoInteractions(rejectedRowStagingService);
    }

    @Test
    void constructorRejectsNegativeRetentionDuration() {
        assertThatThrownBy(() -> new TransactionImportRejectedRowCleanupScheduler(
                rejectedRowStagingService,
                retentionService,
                retentionMetrics,
                Duration.ofHours(-1),
                FAILED_IMPORT_RECOVERY_WINDOW,
                ABANDONMENT_BATCH_SIZE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rejected-row staging retention duration must be positive");

        verifyNoInteractions(rejectedRowStagingService);
    }

    @Test
    void abandonExpiredFailedImportsUsesRecoveryWindowAndRecordsResults() {
        when(retentionService.abandonStaleFailedImports(
                any(Instant.class),
                eq(ABANDONMENT_BATCH_SIZE)
        )).thenReturn(new TransactionImportAbandonmentResult(2, 7));

        Instant invocationStartedAt = Instant.now();

        scheduler().abandonExpiredFailedImports();

        Instant invocationFinishedAt = Instant.now();
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(retentionService).abandonStaleFailedImports(
                cutoffCaptor.capture(),
                eq(ABANDONMENT_BATCH_SIZE)
        );

        assertThat(cutoffCaptor.getValue()).isBetween(
                invocationStartedAt.minus(FAILED_IMPORT_RECOVERY_WINDOW),
                invocationFinishedAt.minus(FAILED_IMPORT_RECOVERY_WINDOW)
        );

        verify(retentionMetrics).recordAbandonedImports(2);
        verify(retentionMetrics).recordDeletedStagingRows(7);
    }

    @Test
    void abandonExpiredFailedImportsRecordsFailureWithoutEscapingScheduler() {
        RuntimeException cause = new RuntimeException("Database unavailable");

        when(retentionService.abandonStaleFailedImports(
                any(Instant.class),
                eq(ABANDONMENT_BATCH_SIZE)
        )).thenThrow(cause);

        assertThatCode(() -> scheduler().abandonExpiredFailedImports())
                .doesNotThrowAnyException();

        verify(retentionMetrics).recordFailure();
    }

    @Test
    void constructorRejectsMissingFailedImportRecoveryWindow() {
        assertThatThrownBy(() -> new TransactionImportRejectedRowCleanupScheduler(
                rejectedRowStagingService,
                retentionService,
                retentionMetrics,
                RETENTION_DURATION,
                null,
                ABANDONMENT_BATCH_SIZE
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Failed-import recovery window is required");
    }

    @Test
    void constructorRejectsNonPositiveAbandonmentBatchSize() {
        assertThatThrownBy(() -> new TransactionImportRejectedRowCleanupScheduler(
                rejectedRowStagingService,
                retentionService,
                retentionMetrics,
                RETENTION_DURATION,
                FAILED_IMPORT_RECOVERY_WINDOW,
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failed-import abandonment batch size must be positive");
    }

    private TransactionImportRejectedRowCleanupScheduler scheduler() {
        return new TransactionImportRejectedRowCleanupScheduler(
                rejectedRowStagingService,
                retentionService,
                retentionMetrics,
                RETENTION_DURATION,
                FAILED_IMPORT_RECOVERY_WINDOW,
                ABANDONMENT_BATCH_SIZE
        );
    }
}