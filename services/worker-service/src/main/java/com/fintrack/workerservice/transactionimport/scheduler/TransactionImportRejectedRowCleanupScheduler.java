package com.fintrack.workerservice.transactionimport.scheduler;

import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportRejectedRowStagingService;
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

    public TransactionImportRejectedRowCleanupScheduler(TransactionImportRejectedRowStagingService rejectedRowStagingService,
                                                        @Value("${fintrack.batch.rejected-row-staging-retention}") Duration retentionDuration) {

        Objects.requireNonNull(retentionDuration, "Rejected-row staging retention duration is required");

        if (retentionDuration.isZero() || retentionDuration.isNegative()) {
            throw new IllegalArgumentException("Rejected-row staging retention duration must be positive");
        }

        this.rejectedRowStagingService = rejectedRowStagingService;
        this.retentionDuration = retentionDuration;
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
}