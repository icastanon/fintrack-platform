package com.fintrack.workerservice.transactionimport.model;

import lombok.Getter;

import java.util.UUID;

@Getter
public class TransactionImportProcessingAttempt {

    private static final int MAXIMUM_PROCESSING_OWNER_LENGTH = 100;

    private final UUID eventId;
    private final Long importId;
    private final Long accountId;
    private final Long userId;
    private final String processingOwner;
    private final long fencingToken;

    public TransactionImportProcessingAttempt(UUID eventId,
                                              Long importId,
                                              Long accountId,
                                              Long userId,
                                              String processingOwner,
                                              long fencingToken) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID is required");
        }

        if (importId == null || importId <= 0) {
            throw new IllegalArgumentException("Import ID must be positive");
        }

        if (accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("Account ID must be positive");
        }

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }

        if (processingOwner == null || processingOwner.isBlank()) {
            throw new IllegalArgumentException("Processing owner is required");
        }

        String normalizedOwner = processingOwner.trim();

        if (normalizedOwner.length() > MAXIMUM_PROCESSING_OWNER_LENGTH) {
            throw new IllegalArgumentException(
                    "Processing owner cannot exceed " + MAXIMUM_PROCESSING_OWNER_LENGTH + " characters"
            );
        }

        if (fencingToken <= 0) {
            throw new IllegalArgumentException("Processing fencing token must be positive");
        }

        this.eventId = eventId;
        this.importId = importId;
        this.accountId = accountId;
        this.userId = userId;
        this.processingOwner = normalizedOwner;
        this.fencingToken = fencingToken;
    }
}