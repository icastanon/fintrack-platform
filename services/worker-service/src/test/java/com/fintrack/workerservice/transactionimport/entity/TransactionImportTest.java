package com.fintrack.workerservice.transactionimport.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionImportTest {

    @Test
    void markRunningTransitionsQueuedImportAndSetsInitialStartTime() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.QUEUED);
        Instant before = Instant.now();

        transactionImport.markRunning();

        Instant after = Instant.now();

        assertThat(transactionImport.getStatus()).isEqualTo(TransactionImportStatus.RUNNING);
        assertThat(transactionImport.getStartedAt()).isBetween(before, after);
        assertThat(transactionImport.getCompletedAt()).isNull();
        assertThat(transactionImport.getFailureSummary()).isNull();
    }

    @Test
    void markRunningPreservesOriginalStartTimeWhenRestartingFailedImport() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.FAILED);
        Instant originalStartedAt = Instant.parse("2026-08-12T12:00:00Z");

        ReflectionTestUtils.setField(transactionImport, "startedAt", originalStartedAt);
        ReflectionTestUtils.setField(
                transactionImport,
                "completedAt",
                Instant.parse("2026-08-12T12:05:00Z")
        );
        ReflectionTestUtils.setField(transactionImport, "failureSummary", "Temporary failure");

        transactionImport.markRunning();

        assertThat(transactionImport.getStatus()).isEqualTo(TransactionImportStatus.RUNNING);
        assertThat(transactionImport.getStartedAt()).isEqualTo(originalStartedAt);
        assertThat(transactionImport.getCompletedAt()).isNull();
        assertThat(transactionImport.getFailureSummary()).isNull();
    }

    @Test
    void markRunningIsIdempotentForRunningImport() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);
        Instant originalStartedAt = Instant.parse("2026-08-12T12:00:00Z");

        ReflectionTestUtils.setField(transactionImport, "startedAt", originalStartedAt);

        transactionImport.markRunning();

        assertThat(transactionImport.getStatus()).isEqualTo(TransactionImportStatus.RUNNING);
        assertThat(transactionImport.getStartedAt()).isEqualTo(originalStartedAt);
    }

    @Test
    void markRunningRejectsCompletedImport() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.COMPLETED);
        Instant completedAt = Instant.parse("2026-08-12T12:05:00Z");

        ReflectionTestUtils.setField(transactionImport, "completedAt", completedAt);

        assertThatThrownBy(transactionImport::markRunning)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A completed transaction import cannot be restarted");

        assertThat(transactionImport.getStatus()).isEqualTo(TransactionImportStatus.COMPLETED);
        assertThat(transactionImport.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void markCompletedStoresFinalCountersAndCompletionTime() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);
        Instant before = Instant.now();

        transactionImport.markCompleted(8, 2, 1);

        Instant after = Instant.now();

        assertThat(transactionImport.getStatus()).isEqualTo(TransactionImportStatus.COMPLETED);
        assertThat(transactionImport.getTotalRows()).isEqualTo(11);
        assertThat(transactionImport.getProcessedRows()).isEqualTo(11);
        assertThat(transactionImport.getSuccessfulRows()).isEqualTo(8);
        assertThat(transactionImport.getSkippedRows()).isEqualTo(2);
        assertThat(transactionImport.getFailedRows()).isEqualTo(1);
        assertThat(transactionImport.getFailureSummary()).isNull();
        assertThat(transactionImport.getCompletedAt()).isBetween(before, after);
    }

    @Test
    void markCompletedIsIdempotentForCompletedImport() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.COMPLETED);
        Instant originalCompletedAt = Instant.parse("2026-08-12T12:05:00Z");

        ReflectionTestUtils.setField(transactionImport, "totalRows", 5L);
        ReflectionTestUtils.setField(transactionImport, "processedRows", 5L);
        ReflectionTestUtils.setField(transactionImport, "successfulRows", 5L);
        ReflectionTestUtils.setField(transactionImport, "skippedRows", 0L);
        ReflectionTestUtils.setField(transactionImport, "failedRows", 0L);
        ReflectionTestUtils.setField(transactionImport, "completedAt", originalCompletedAt);

        transactionImport.markCompleted(10, 2, 1);

        assertThat(transactionImport.getTotalRows()).isEqualTo(5);
        assertThat(transactionImport.getProcessedRows()).isEqualTo(5);
        assertThat(transactionImport.getSuccessfulRows()).isEqualTo(5);
        assertThat(transactionImport.getSkippedRows()).isZero();
        assertThat(transactionImport.getFailedRows()).isZero();
        assertThat(transactionImport.getCompletedAt()).isEqualTo(originalCompletedAt);
    }

    @Test
    void markCompletedRejectsImportThatIsNotRunningOrCompleted() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.QUEUED);

        assertThatThrownBy(() -> transactionImport.markCompleted(1, 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only a running transaction import can be completed");
    }

    @Test
    void markFailedStoresPartialCountersAndNormalizedFailureSummary() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);
        Instant before = Instant.now();

        transactionImport.markFailed(4, 1, 0, "  Temporary database failure  ");

        Instant after = Instant.now();

        assertThat(transactionImport.getStatus()).isEqualTo(TransactionImportStatus.FAILED);
        assertThat(transactionImport.getTotalRows()).isNull();
        assertThat(transactionImport.getProcessedRows()).isEqualTo(5);
        assertThat(transactionImport.getSuccessfulRows()).isEqualTo(4);
        assertThat(transactionImport.getSkippedRows()).isEqualTo(1);
        assertThat(transactionImport.getFailedRows()).isZero();
        assertThat(transactionImport.getFailureSummary()).isEqualTo("Temporary database failure");
        assertThat(transactionImport.getCompletedAt()).isBetween(before, after);
    }

    @Test
    void markFailedTruncatesOversizedFailureSummary() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);

        transactionImport.markFailed(0, 0, 0, "a".repeat(1100));

        assertThat(transactionImport.getFailureSummary()).hasSize(1000);
    }

    @Test
    void markFailedRejectsCompletedImport() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.COMPLETED);

        assertThatThrownBy(() -> transactionImport.markFailed(1, 0, 0, "Failure"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A completed transaction import cannot be failed");
    }

    @Test
    void terminalTransitionsRejectNegativeCounters() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);

        assertThatThrownBy(() -> transactionImport.markCompleted(-1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transaction import row counts cannot be negative");
    }

    @Test
    void markFailedRejectsBlankFailureSummary() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);

        assertThatThrownBy(() -> transactionImport.markFailed(0, 0, 0, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transaction import failure summary is required");
    }

    private TransactionImport transactionImport(TransactionImportStatus status) {
        TransactionImport transactionImport = new TransactionImport();
        ReflectionTestUtils.setField(transactionImport, "status", status);
        return transactionImport;
    }
}