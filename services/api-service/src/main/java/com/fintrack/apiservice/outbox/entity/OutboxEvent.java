package com.fintrack.apiservice.outbox.entity;

import java.util.Map;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private Integer eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "lock_owner", length = 100)
    private String lockOwner;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static OutboxEvent create(
            UUID eventId,
            String aggregateType,
            Long aggregateId,
            String eventType,
            Integer eventVersion,
            Map<String, Object> payload
    ) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.eventId = eventId;
        outboxEvent.aggregateType = aggregateType;
        outboxEvent.aggregateId = aggregateId;
        outboxEvent.eventType = eventType;
        outboxEvent.eventVersion = eventVersion;
        outboxEvent.payload = payload;
        outboxEvent.status = OutboxEventStatus.PENDING;
        outboxEvent.attemptCount = 0;
        outboxEvent.availableAt = Instant.now();
        return outboxEvent;
    }

    public void claim(String lockOwner, Instant lockedAt) {
        if (status != OutboxEventStatus.PENDING) {
            throw new IllegalStateException("Only pending outbox events can be claimed");
        }

        if (lockOwner == null || lockOwner.isBlank()) {
            throw new IllegalArgumentException("Lock owner is required");
        }

        this.status = OutboxEventStatus.PROCESSING;
        this.lockOwner = lockOwner;
        this.lockedAt = Objects.requireNonNull(lockedAt, "Locked at is required");
        this.attemptCount++;
    }

    public void markPublished(String lockOwner, Instant publishedAt) {
        validateClaimOwner(lockOwner);

        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(publishedAt, "Published at is required");
        this.lockedAt = null;
        this.lockOwner = null;
        this.lastError = null;
    }

    public void rescheduleAfterFailure(String lockOwner, Instant availableAt, String lastError) {
        validateClaimOwner(lockOwner);

        this.status = OutboxEventStatus.PENDING;
        this.availableAt = Objects.requireNonNull(availableAt, "Available at is required");
        this.lockedAt = null;
        this.lockOwner = null;
        this.lastError = normalizeError(lastError);
    }

    public void markFailed(String lockOwner, String lastError) {
        validateClaimOwner(lockOwner);

        this.status = OutboxEventStatus.FAILED;
        this.lockedAt = null;
        this.lockOwner = null;
        this.lastError = normalizeError(lastError);
    }

    public void recoverStaleClaim(Instant availableAt) {
        if (status != OutboxEventStatus.PROCESSING) {
            throw new IllegalStateException("Only processing outbox events can be recovered");
        }

        this.status = OutboxEventStatus.PENDING;
        this.availableAt = Objects.requireNonNull(availableAt, "Available at is required");
        this.lockedAt = null;
        this.lockOwner = null;
        this.lastError = "Recovered stale relay claim";
    }

    private void validateClaimOwner(String requestedLockOwner) {
        if (status != OutboxEventStatus.PROCESSING) {
            throw new IllegalStateException("Outbox event is not being processed");
        }

        if (requestedLockOwner == null || requestedLockOwner.isBlank()) {
            throw new IllegalArgumentException("Lock owner is required");
        }

        if (!Objects.equals(lockOwner, requestedLockOwner)) {
            throw new IllegalStateException("Outbox event is owned by another relay");
        }
    }

    private String normalizeError(String error) {
        String normalizedError = error == null || error.isBlank()
                ? "Unknown publication failure"
                : error.trim();

        if (normalizedError.length() <= MAX_ERROR_LENGTH) {
            return normalizedError;
        }

        return normalizedError.substring(0, MAX_ERROR_LENGTH);
    }
}