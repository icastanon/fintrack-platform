package com.fintrack.workerservice.transactionimport.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionImportProcessingLeaseAcquisitionTest {

    private static final UUID EVENT_ID =
            UUID.fromString("a35c1351-d184-4014-b886-c1fbb8c7eec2");

    @Test
    void acquiredStoresProcessingAttempt() {
        TransactionImportProcessingAttempt processingAttempt = processingAttempt();

        TransactionImportProcessingLeaseAcquisition acquisition =
                TransactionImportProcessingLeaseAcquisition.acquired(processingAttempt);

        assertThat(acquisition.getOutcome())
                .isEqualTo(TransactionImportProcessingLeaseAcquisition.Outcome.ACQUIRED);
        assertThat(acquisition.getProcessingAttempt()).isSameAs(processingAttempt);
        assertThat(acquisition.isAcquired()).isTrue();
    }

    @Test
    void acquiredRejectsMissingProcessingAttempt() {
        assertThatThrownBy(() ->
                TransactionImportProcessingLeaseAcquisition.acquired(null)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Processing attempt is required");
    }

    @Test
    void activeLeaseContainsNoProcessingAttempt() {
        TransactionImportProcessingLeaseAcquisition acquisition =
                TransactionImportProcessingLeaseAcquisition.activeLease();

        assertThat(acquisition.getOutcome())
                .isEqualTo(TransactionImportProcessingLeaseAcquisition.Outcome.ACTIVE_LEASE);
        assertThat(acquisition.getProcessingAttempt()).isNull();
        assertThat(acquisition.isAcquired()).isFalse();
    }

    @Test
    void alreadyCompletedContainsNoProcessingAttempt() {
        TransactionImportProcessingLeaseAcquisition acquisition =
                TransactionImportProcessingLeaseAcquisition.alreadyCompleted();

        assertThat(acquisition.getOutcome())
                .isEqualTo(TransactionImportProcessingLeaseAcquisition.Outcome.ALREADY_COMPLETED);
        assertThat(acquisition.getProcessingAttempt()).isNull();
        assertThat(acquisition.isAcquired()).isFalse();
    }

    private TransactionImportProcessingAttempt processingAttempt() {
        return new TransactionImportProcessingAttempt(
                EVENT_ID,
                41L,
                22L,
                9L,
                "worker-attempt-123",
                3L
        );
    }

    @Test
    void alreadyAbandonedContainsNoProcessingAttempt() {
        TransactionImportProcessingLeaseAcquisition acquisition =
                TransactionImportProcessingLeaseAcquisition.alreadyAbandoned();

        assertThat(acquisition.getOutcome())
                .isEqualTo(TransactionImportProcessingLeaseAcquisition.Outcome.ALREADY_ABANDONED);
        assertThat(acquisition.getProcessingAttempt()).isNull();
        assertThat(acquisition.isAcquired()).isFalse();
    }
}