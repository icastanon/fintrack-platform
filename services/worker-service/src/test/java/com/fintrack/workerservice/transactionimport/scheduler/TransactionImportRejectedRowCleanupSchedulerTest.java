package com.fintrack.workerservice.transactionimport.scheduler;

import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportRejectedRowStagingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportRejectedRowCleanupSchedulerTest {

    private static final Duration RETENTION_DURATION = Duration.ofDays(1);

    @Mock
    private TransactionImportRejectedRowStagingService rejectedRowStagingService;

    @Test
    void cleanupCompletedImportStagingDeletesRowsOlderThanRetentionDuration() {
        TransactionImportRejectedRowCleanupScheduler scheduler =
                new TransactionImportRejectedRowCleanupScheduler(
                        rejectedRowStagingService,
                        RETENTION_DURATION
                );

        when(rejectedRowStagingService.deleteAllForCompletedImportsBefore(
                org.mockito.ArgumentMatchers.any(Instant.class)))
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
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Rejected-row staging retention duration is required");

        verifyNoInteractions(rejectedRowStagingService);
    }

    @Test
    void constructorRejectsZeroRetentionDuration() {
        assertThatThrownBy(() -> new TransactionImportRejectedRowCleanupScheduler(
                rejectedRowStagingService,
                Duration.ZERO
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rejected-row staging retention duration must be positive");

        verifyNoInteractions(rejectedRowStagingService);
    }

    @Test
    void constructorRejectsNegativeRetentionDuration() {
        assertThatThrownBy(() -> new TransactionImportRejectedRowCleanupScheduler(
                rejectedRowStagingService,
                Duration.ofHours(-1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rejected-row staging retention duration must be positive");

        verifyNoInteractions(rejectedRowStagingService);
    }
}