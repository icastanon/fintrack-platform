package com.fintrack.workerservice.transactionimport.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionImportTest {

    private static final String REJECTED_OBJECT_KEY = "imports/9/import-uuid/rejected.csv";
    private static final String PROCESSING_OWNER = "worker-attempt-123";
    private static final Instant CLAIMED_AT = Instant.parse("2026-08-14T12:00:00Z");
    private static final Instant LEASE_EXPIRES_AT = Instant.parse("2026-08-14T12:02:00Z");

    @Test
    void claimProcessingLeaseStoresOwnershipAndTransitionsImportToRunning() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.QUEUED);

        long fencingToken =
                transactionImport.claimProcessingLease(PROCESSING_OWNER,
                        CLAIMED_AT,
                        LEASE_EXPIRES_AT);

        assertThat(fencingToken).isEqualTo(1);
        assertThat(transactionImport.getProcessingOwner()).isEqualTo(PROCESSING_OWNER);
        assertThat(transactionImport.getProcessingLeaseExpiresAt()).isEqualTo(LEASE_EXPIRES_AT);
        assertThat(transactionImport.getProcessingFencingToken()).isEqualTo(1);
        assertThat(transactionImport.getStatus()).isEqualTo(TransactionImportStatus.RUNNING);
        assertThat(transactionImport.getStartedAt()).isEqualTo(CLAIMED_AT);
        assertThat(transactionImport.getCompletedAt()).isNull();
        assertThat(transactionImport.getFailureSummary()).isNull();
    }

    @Test
    void claimProcessingLeaseReplacesOwnershipAndIncrementsFencingToken() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);

        ReflectionTestUtils.setField(transactionImport, "processingOwner", "old-worker");
        ReflectionTestUtils.setField(transactionImport,
                "processingLeaseExpiresAt",
                Instant.parse("2026-08-14T11:59:00Z"));
        ReflectionTestUtils.setField(transactionImport, "processingFencingToken", 4L);

        long fencingToken =
                transactionImport.claimProcessingLease(PROCESSING_OWNER,
                        CLAIMED_AT,
                        LEASE_EXPIRES_AT);

        assertThat(fencingToken).isEqualTo(5);
        assertThat(transactionImport.getProcessingOwner()).isEqualTo(PROCESSING_OWNER);
        assertThat(transactionImport.getProcessingLeaseExpiresAt()).isEqualTo(LEASE_EXPIRES_AT);
        assertThat(transactionImport.getProcessingFencingToken()).isEqualTo(5);
    }

    @Test
    void claimProcessingLeaseRestartsFailedImportAndPreservesOriginalStartTime() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.FAILED);
        Instant originalStartedAt = Instant.parse("2026-08-12T12:00:00Z");

        ReflectionTestUtils.setField(transactionImport, "startedAt", originalStartedAt);
        ReflectionTestUtils.setField(transactionImport,
                "completedAt",
                Instant.parse("2026-08-12T12:05:00Z"));
        ReflectionTestUtils.setField(transactionImport, "failureSummary", "Temporary failure");
        ReflectionTestUtils.setField(transactionImport, "processingFencingToken", 2L);

        long fencingToken =
                transactionImport.claimProcessingLease(PROCESSING_OWNER,
                        CLAIMED_AT,
                        LEASE_EXPIRES_AT);

        assertThat(fencingToken).isEqualTo(3);
        assertThat(transactionImport.getStatus()).isEqualTo(TransactionImportStatus.RUNNING);
        assertThat(transactionImport.getStartedAt()).isEqualTo(originalStartedAt);
        assertThat(transactionImport.getCompletedAt()).isNull();
        assertThat(transactionImport.getFailureSummary()).isNull();
        assertThat(transactionImport.getProcessingOwner()).isEqualTo(PROCESSING_OWNER);
    }

    @Test
    void claimProcessingLeasePreservesOriginalStartTimeForRunningImport() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);
        Instant originalStartedAt = Instant.parse("2026-08-12T12:00:00Z");

        ReflectionTestUtils.setField(transactionImport, "startedAt", originalStartedAt);

        transactionImport.claimProcessingLease(PROCESSING_OWNER,
                CLAIMED_AT,
                LEASE_EXPIRES_AT);

        assertThat(transactionImport.getStatus()).isEqualTo(TransactionImportStatus.RUNNING);
        assertThat(transactionImport.getStartedAt()).isEqualTo(originalStartedAt);
    }

    @Test
    void hasActiveProcessingLeaseReturnsTrueBeforeExpiration() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);

        ReflectionTestUtils.setField(transactionImport, "processingOwner", PROCESSING_OWNER);
        ReflectionTestUtils.setField(transactionImport, "processingLeaseExpiresAt", LEASE_EXPIRES_AT);

        assertThat(transactionImport.hasActiveProcessingLease(CLAIMED_AT)).isTrue();
    }

    @Test
    void hasActiveProcessingLeaseReturnsFalseAtExpiration() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);

        ReflectionTestUtils.setField(transactionImport, "processingOwner", PROCESSING_OWNER);
        ReflectionTestUtils.setField(transactionImport, "processingLeaseExpiresAt", LEASE_EXPIRES_AT);

        assertThat(transactionImport.hasActiveProcessingLease(LEASE_EXPIRES_AT)).isFalse();
    }

    @Test
    void hasActiveProcessingLeaseReturnsFalseWithoutOwner() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.QUEUED);

        assertThat(transactionImport.hasActiveProcessingLease(CLAIMED_AT)).isFalse();
    }

    @Test
    void claimProcessingLeaseRejectsCompletedImport() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.COMPLETED);

        assertThatThrownBy(() ->
                transactionImport.claimProcessingLease(PROCESSING_OWNER,
                        CLAIMED_AT,
                        LEASE_EXPIRES_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A completed transaction import cannot be claimed");
    }

    @Test
    void claimProcessingLeaseRejectsBlankOwner() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.QUEUED);

        assertThatThrownBy(() ->
                transactionImport.claimProcessingLease(" ", CLAIMED_AT, LEASE_EXPIRES_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Processing owner is required");
    }

    @Test
    void claimProcessingLeaseRejectsExpirationThatIsNotAfterClaimTime() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.QUEUED);

        assertThatThrownBy(() ->
                transactionImport.claimProcessingLease(PROCESSING_OWNER,
                        CLAIMED_AT,
                        CLAIMED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Processing lease expiration must be after the claim time");
    }

    @Test
    void markCompletedStoresFinalCountersRejectedObjectAndCompletionTime() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);
        Instant before = Instant.now();

        transactionImport.markCompleted(8, 2, 0, REJECTED_OBJECT_KEY);

        Instant after = Instant.now();

        assertThat(transactionImport.getStatus()).isEqualTo(TransactionImportStatus.COMPLETED);
        assertThat(transactionImport.getTotalRows()).isEqualTo(10);
        assertThat(transactionImport.getProcessedRows()).isEqualTo(10);
        assertThat(transactionImport.getSuccessfulRows()).isEqualTo(8);
        assertThat(transactionImport.getSkippedRows()).isEqualTo(2);
        assertThat(transactionImport.getFailedRows()).isZero();
        assertThat(transactionImport.getRejectedObjectKey()).isEqualTo(REJECTED_OBJECT_KEY);
        assertThat(transactionImport.getFailureSummary()).isNull();
        assertThat(transactionImport.getCompletedAt()).isBetween(before, after);
    }

    @Test
    void markCompletedWithoutSkippedRowsStoresNullRejectedObjectKey() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);

        transactionImport.markCompleted(8, 0, 0, null);

        assertThat(transactionImport.getStatus()).isEqualTo(TransactionImportStatus.COMPLETED);
        assertThat(transactionImport.getSuccessfulRows()).isEqualTo(8);
        assertThat(transactionImport.getSkippedRows()).isZero();
        assertThat(transactionImport.getRejectedObjectKey()).isNull();
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
        ReflectionTestUtils.setField(transactionImport, "rejectedObjectKey", null);
        ReflectionTestUtils.setField(transactionImport, "completedAt", originalCompletedAt);

        transactionImport.markCompleted(10, 2, 0, REJECTED_OBJECT_KEY);

        assertThat(transactionImport.getTotalRows()).isEqualTo(5);
        assertThat(transactionImport.getProcessedRows()).isEqualTo(5);
        assertThat(transactionImport.getSuccessfulRows()).isEqualTo(5);
        assertThat(transactionImport.getSkippedRows()).isZero();
        assertThat(transactionImport.getFailedRows()).isZero();
        assertThat(transactionImport.getRejectedObjectKey()).isNull();
        assertThat(transactionImport.getCompletedAt()).isEqualTo(originalCompletedAt);
    }

    @Test
    void markCompletedRejectsImportThatIsNotRunningOrCompleted() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.QUEUED);

        assertThatThrownBy(() -> transactionImport.markCompleted(1, 0, 0, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only a running transaction import can be completed");
    }

    @Test
    void markCompletedRequiresRejectedObjectKeyWhenSkippedRowsExist() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);

        assertThatThrownBy(() -> transactionImport.markCompleted(8, 2, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A rejected output object key is required when skipped rows exist");
    }

    @Test
    void markCompletedRejectsBlankObjectKeyWhenSkippedRowsExist() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);

        assertThatThrownBy(() -> transactionImport.markCompleted(8, 2, 0, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A rejected output object key is required when skipped rows exist");
    }

    @Test
    void markCompletedRejectsObjectKeyWithoutSkippedRows() {
        TransactionImport transactionImport = transactionImport(TransactionImportStatus.RUNNING);

        assertThatThrownBy(() ->
                transactionImport.markCompleted(8, 0, 0, REJECTED_OBJECT_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A rejected output object key cannot exist without skipped rows");
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
        assertThat(transactionImport.getRejectedObjectKey()).isNull();
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

        assertThatThrownBy(() -> transactionImport.markCompleted(-1, 0, 0, null))
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