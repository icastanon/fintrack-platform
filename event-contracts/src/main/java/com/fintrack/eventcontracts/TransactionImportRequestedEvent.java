package com.fintrack.eventcontracts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TransactionImportRequestedEvent {

    public static final int CURRENT_VERSION = 1;

    private final UUID eventId;
    private final int eventVersion;
    private final Long importId;
    private final Long accountId;
    private final Long userId;
    private final String sourceObjectKey;
    private final String correlationId;
    private final Instant occurredAt;

    @JsonCreator
    public TransactionImportRequestedEvent(@JsonProperty("eventId") UUID eventId,
                                           @JsonProperty("eventVersion") int eventVersion,
                                           @JsonProperty("importId") Long importId,
                                           @JsonProperty("accountId") Long accountId,
                                           @JsonProperty("userId") Long userId,
                                           @JsonProperty("sourceObjectKey") String sourceObjectKey,
                                           @JsonProperty("correlationId") String correlationId,
                                           @JsonProperty("occurredAt") Instant occurredAt) {
        if (eventVersion < 1) {
            throw new IllegalArgumentException("Event version must be positive");
        }

        this.eventId = Objects.requireNonNull(eventId, "Event ID is required");
        this.eventVersion = eventVersion;
        this.importId = Objects.requireNonNull(importId, "Import ID is required");
        this.accountId = Objects.requireNonNull(accountId, "Account ID is required");
        this.userId = Objects.requireNonNull(userId, "User ID is required");
        this.sourceObjectKey = requireText(sourceObjectKey, "Source object key is required");
        this.correlationId = correlationId == null || correlationId.isBlank() ? eventId.toString() : correlationId;
        this.occurredAt = Objects.requireNonNull(occurredAt, "Occurred at is required");
    }

    public static TransactionImportRequestedEvent create(UUID eventId,
                                                         Long importId,
                                                         Long accountId,
                                                         Long userId,
                                                         String sourceObjectKey,
                                                         String correlationId,
                                                         Instant occurredAt) {
        return new TransactionImportRequestedEvent(
                eventId,
                CURRENT_VERSION,
                importId,
                accountId,
                userId,
                sourceObjectKey,
                correlationId,
                occurredAt
        );
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    public UUID getEventId() {
        return eventId;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public Long getImportId() {
        return importId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSourceObjectKey() {
        return sourceObjectKey;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}