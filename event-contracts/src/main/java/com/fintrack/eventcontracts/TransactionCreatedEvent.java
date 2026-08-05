package com.fintrack.eventcontracts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TransactionCreatedEvent {

    public static final int CURRENT_VERSION = 1;

    private final UUID eventId;
    private final int eventVersion;
    private final Long transactionId;
    private final Long userId;
    private final Instant occurredAt;

    @JsonCreator
    public TransactionCreatedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("eventVersion") int eventVersion,
            @JsonProperty("transactionId") Long transactionId,
            @JsonProperty("userId") Long userId,
            @JsonProperty("occurredAt") Instant occurredAt
    ) {
        if (eventVersion < 1) {
            throw new IllegalArgumentException("Event version must be positive");
        }

        this.eventId = Objects.requireNonNull(eventId, "Event ID is required");
        this.eventVersion = eventVersion;
        this.transactionId = Objects.requireNonNull(transactionId, "Transaction ID is required");
        this.userId = Objects.requireNonNull(userId, "User ID is required");
        this.occurredAt = Objects.requireNonNull(occurredAt, "Occurred at is required");
    }

    public static TransactionCreatedEvent create(UUID eventId, Long transactionId, Long userId, Instant occurredAt) {
        return new TransactionCreatedEvent(eventId, CURRENT_VERSION, transactionId, userId, occurredAt);
    }

    public UUID getEventId() {
        return eventId;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}