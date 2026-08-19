package com.fintrack.workerservice.transactionimport.scheduler;

import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportRejectedRowStagingService;
import com.fintrack.workerservice.transactionimport.metrics.TransactionImportRetentionMetrics;
import com.fintrack.workerservice.transactionimport.model.TransactionImportAbandonmentResult;
import com.fintrack.workerservice.transactionimport.service.TransactionImportRetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Component
public class TransactionImportRejectedRowCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionImportRejectedRowCleanupScheduler.class);

    private final TransactionImportRejectedRowStagingService rejectedRowStagingService;
    private final Duration retentionDuration;
    private final TransactionImportRetentionService retentionService;
    private final TransactionImportRetentionMetrics retentionMetrics;
    private final Duration failedImportRecoveryWindow;
    private final int failedImportAbandonmentBatchSize;

    public TransactionImportRejectedRowCleanupScheduler(
            TransactionImportRejectedRowStagingService rejectedRowStagingService,
            TransactionImportRetentionService retentionService,
            TransactionImportRetentionMetrics retentionMetrics,
            @Value("${fintrack.batch.rejected-row-staging-retention}") Duration retentionDuration,
            @Value("${fintrack.batch.failed-import-recovery-window}") Duration failedImportRecoveryWindow,
            @Value("${fintrack.batch.failed-import-abandonment-batch-size}") int failedImportAbandonmentBatchSize) {

        Objects.requireNonNull(retentionDuration, "Rejected-row staging retention duration is required");
        Objects.requireNonNull(failedImportRecoveryWindow, "Failed-import recovery window is required");

        if (retentionDuration.isZero() || retentionDuration.isNegative()) {
            throw new IllegalArgumentException("Rejected-row staging retention duration must be positive");
        }

        if (failedImportRecoveryWindow.isZero() || failedImportRecoveryWindow.isNegative()) {
            throw new IllegalArgumentException("Failed-import recovery window must be positive");
        }

        if (failedImportAbandonmentBatchSize <= 0) {
            throw new IllegalArgumentException("Failed-import abandonment batch size must be positive");
        }

        this.rejectedRowStagingService = rejectedRowStagingService;
        this.retentionService = retentionService;
        this.retentionMetrics = retentionMetrics;
        this.retentionDuration = retentionDuration;
        this.failedImportRecoveryWindow = failedImportRecoveryWindow;
        this.failedImportAbandonmentBatchSize = failedImportAbandonmentBatchSize;
    }

    @Scheduled(fixedDelayString = "${fintrack.batch.rejected-row-staging-cleanup-delay}",
            initialDelayString = "${fintrack.batch.rejected-row-staging-cleanup-initial-delay}",
            scheduler = "transactionImportRetentionTaskScheduler")
    public void cleanupCompletedImportStaging() {
        Instant completedBefore = Instant.now().minus(retentionDuration);
        int deletedRows = rejectedRowStagingService.deleteAllForCompletedImportsBefore(completedBefore);

        if (deletedRows > 0) {
            LOGGER.info(
                    "Deleted expired completed-import rejected-row staging: deletedRows={}, completedBefore={}",
                    deletedRows,
                    completedBefore
            );
        }
    }

    @Scheduled(fixedDelayString = "${fintrack.batch.failed-import-abandonment-delay}",
            initialDelayString = "${fintrack.batch.failed-import-abandonment-initial-delay}",
            scheduler = "transactionImportRetentionTaskScheduler")
    public void abandonExpiredFailedImports() {
        Instant failedBefore = Instant.now().minus(failedImportRecoveryWindow);

        try {
            TransactionImportAbandonmentResult result =
                    retentionService.abandonStaleFailedImports(
                            failedBefore,
                            failedImportAbandonmentBatchSize
                    );

            retentionMetrics.recordAbandonedImports(result.getAbandonedImportCount());
            retentionMetrics.recordDeletedStagingRows(result.getDeletedRejectedRowCount());

            if (result.getAbandonedImportCount() > 0) {
                LOGGER.info(
                        "Abandoned expired failed imports: abandonedImports={}, deletedStagingRows={}, failedBefore={}",
                        result.getAbandonedImportCount(),
                        result.getDeletedRejectedRowCount(),
                        failedBefore
                );
            }
        } catch (RuntimeException exception) {
            retentionMetrics.recordFailure();

            LOGGER.error(
                    "Failed to abandon expired failed transaction imports: failedBefore={}",
                    failedBefore,
                    exception
            );
        }
    }
}