package com.fintrack.workerservice.transactionimport.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TransactionImportProcessingLeaseAcquisition {

    public enum Outcome {
        ACQUIRED,
        ACTIVE_LEASE,
        ALREADY_COMPLETED,
        ALREADY_ABANDONED
    }

    private final Outcome outcome;
    private final TransactionImportProcessingAttempt processingAttempt;

    public static TransactionImportProcessingLeaseAcquisition acquired(TransactionImportProcessingAttempt processingAttempt) {
        if (processingAttempt == null) {
            throw new IllegalArgumentException("Processing attempt is required");
        }

        return new TransactionImportProcessingLeaseAcquisition(
                Outcome.ACQUIRED,
                processingAttempt
        );
    }

    public static TransactionImportProcessingLeaseAcquisition activeLease() {
        return new TransactionImportProcessingLeaseAcquisition(
                Outcome.ACTIVE_LEASE,
                null
        );
    }

    public static TransactionImportProcessingLeaseAcquisition alreadyCompleted() {
        return new TransactionImportProcessingLeaseAcquisition(
                Outcome.ALREADY_COMPLETED,
                null
        );
    }

    public static TransactionImportProcessingLeaseAcquisition alreadyAbandoned() {
        return new TransactionImportProcessingLeaseAcquisition(
                Outcome.ALREADY_ABANDONED,
                null
        );
    }

    public boolean isAcquired() {
        return outcome == Outcome.ACQUIRED;
    }
}