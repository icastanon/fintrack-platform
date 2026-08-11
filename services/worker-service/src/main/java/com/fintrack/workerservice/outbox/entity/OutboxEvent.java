package com.fintrack.workerservice.outbox.entity;

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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static OutboxEvent create(UUID eventId, String aggregateType,
                                     Long aggregateId, String eventType,
                                     Integer eventVersion, Map<String, Object> payload) {
        OutboxEvent outboxEvent = new OutboxEvent();

        outboxEvent.eventId = Objects.requireNonNull(eventId, "Event ID is required");
        outboxEvent.aggregateType = Objects.requireNonNull(aggregateType, "Aggregate type is required");
        outboxEvent.aggregateId = Objects.requireNonNull(aggregateId, "Aggregate ID is required");
        outboxEvent.eventType = Objects.requireNonNull(eventType, "Event type is required");
        outboxEvent.eventVersion = Objects.requireNonNull(eventVersion, "Event version is required");
        outboxEvent.payload = Objects.requireNonNull(payload, "Payload is required");
        outboxEvent.status = OutboxEventStatus.PENDING;
        outboxEvent.attemptCount = 0;
        outboxEvent.availableAt = Instant.now();

        return outboxEvent;
    }
}