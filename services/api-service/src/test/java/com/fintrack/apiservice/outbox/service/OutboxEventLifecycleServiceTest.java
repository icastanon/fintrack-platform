package com.fintrack.apiservice.outbox.service;

import com.fintrack.apiservice.outbox.entity.OutboxEvent;
import com.fintrack.apiservice.outbox.entity.OutboxEventStatus;
import com.fintrack.apiservice.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventLifecycleServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxEventLifecycleService lifecycleService;

    @Test
    void markPublishedCompletesClaimedEvent() {
        OutboxEvent event = createClaimedEvent("api-instance-1");

        when(outboxEventRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(event));

        lifecycleService.markPublished(51L, "api-instance-1");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLockedAt()).isNull();
        assertThat(event.getLockOwner()).isNull();
        assertThat(event.getLastError()).isNull();

        verify(outboxEventRepository).findByIdForUpdate(51L);
    }

    @Test
    void recordPublicationFailureReschedulesEventWhenAttemptsRemain() {
        OutboxEvent event = createClaimedEvent("api-instance-1");
        Instant previousAvailableAt = event.getAvailableAt();

        when(outboxEventRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(event));

        lifecycleService.recordPublicationFailure(
                51L,
                "api-instance-1",
                "SQS temporarily unavailable",
                3,
                Duration.ofSeconds(30)
        );

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAvailableAt()).isAfter(previousAvailableAt);
        assertThat(event.getLockedAt()).isNull();
        assertThat(event.getLockOwner()).isNull();
        assertThat(event.getLastError()).isEqualTo("SQS temporarily unavailable");
    }

    @Test
    void recordPublicationFailureMarksEventFailedWhenMaximumAttemptsReached() {
        OutboxEvent event = createClaimedEvent("api-instance-1");

        when(outboxEventRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(event));

        lifecycleService.recordPublicationFailure(
                51L,
                "api-instance-1",
                "SQS publication failed",
                1,
                Duration.ofSeconds(30)
        );

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getLockedAt()).isNull();
        assertThat(event.getLockOwner()).isNull();
        assertThat(event.getLastError()).isEqualTo("SQS publication failed");
    }

    @Test
    void markPublishedRejectsDifferentLockOwner() {
        OutboxEvent event = createClaimedEvent("api-instance-1");

        when(outboxEventRepository.findByIdForUpdate(51L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> lifecycleService.markPublished(51L, "api-instance-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Outbox event is owned by another relay");

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
    }

    @Test
    void recoverStaleClaimsReturnsEventsToPending() {
        OutboxEvent firstEvent = createClaimedEvent("dead-instance-1");
        OutboxEvent secondEvent = createClaimedEvent("dead-instance-2");

        when(outboxEventRepository.findStaleProcessingForUpdate(any(Instant.class), org.mockito.ArgumentMatchers.eq(10)))
                .thenReturn(List.of(firstEvent, secondEvent));

        int recoveredCount = lifecycleService.recoverStaleClaims(Duration.ofMinutes(2), 10);

        assertThat(recoveredCount).isEqualTo(2);

        assertThat(firstEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(firstEvent.getLockOwner()).isNull();
        assertThat(firstEvent.getLockedAt()).isNull();
        assertThat(firstEvent.getLastError()).isEqualTo("Recovered stale relay claim");

        assertThat(secondEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(secondEvent.getLockOwner()).isNull();
        assertThat(secondEvent.getLockedAt()).isNull();

        ArgumentCaptor<Instant> staleBeforeCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(outboxEventRepository).findStaleProcessingForUpdate(staleBeforeCaptor.capture(), org.mockito.ArgumentMatchers.eq(10));

        assertThat(staleBeforeCaptor.getValue()).isBefore(Instant.now());
    }

    private OutboxEvent createClaimedEvent(String lockOwner) {
        UUID eventId = UUID.randomUUID();

        Map<String, Object> payload = Map.of(
                "eventId", eventId.toString(),
                "eventVersion", 1,
                "transactionId", 41L,
                "userId", 7L,
                "occurredAt", "2026-08-05T16:30:00Z"
        );

        OutboxEvent event = OutboxEvent.create(
                eventId,
                "FINANCIAL_TRANSACTION",
                41L,
                "TRANSACTION_CREATED",
                1,
                payload
        );

        event.claim(lockOwner, Instant.now());
        return event;
    }
}