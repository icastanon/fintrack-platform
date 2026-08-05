package com.fintrack.apiservice.outbox.service;

import com.fintrack.apiservice.outbox.entity.OutboxEvent;
import com.fintrack.apiservice.outbox.entity.OutboxEventStatus;
import com.fintrack.apiservice.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventClaimServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxEventClaimService outboxEventClaimService;

    @Test
    void claimAvailableEventsMarksReturnedEventsAsProcessing() {
        OutboxEvent firstEvent = createOutboxEvent(41L);
        OutboxEvent secondEvent = createOutboxEvent(42L);

        when(outboxEventRepository.findAvailablePendingForUpdate(2)).thenReturn(List.of(firstEvent, secondEvent));

        List<OutboxEvent> result = outboxEventClaimService.claimAvailableEvents(2, "api-instance-1");

        assertThat(result).containsExactly(firstEvent, secondEvent);

        assertThat(firstEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(firstEvent.getAttemptCount()).isEqualTo(1);
        assertThat(firstEvent.getLockOwner()).isEqualTo("api-instance-1");
        assertThat(firstEvent.getLockedAt()).isNotNull();

        assertThat(secondEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(secondEvent.getAttemptCount()).isEqualTo(1);
        assertThat(secondEvent.getLockOwner()).isEqualTo("api-instance-1");
        assertThat(secondEvent.getLockedAt()).isEqualTo(firstEvent.getLockedAt());

        verify(outboxEventRepository).findAvailablePendingForUpdate(2);
    }

    @Test
    void claimAvailableEventsReturnsEmptyListWhenNoEventsAreAvailable() {
        when(outboxEventRepository.findAvailablePendingForUpdate(10)).thenReturn(List.of());

        List<OutboxEvent> result = outboxEventClaimService.claimAvailableEvents(10, "api-instance-1");

        assertThat(result).isEmpty();

        verify(outboxEventRepository).findAvailablePendingForUpdate(10);
    }

    @Test
    void claimAvailableEventsRejectsInvalidArguments() {
        assertThatThrownBy(() -> outboxEventClaimService.claimAvailableEvents(0, "api-instance-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Batch size must be positive");

        assertThatThrownBy(() -> outboxEventClaimService.claimAvailableEvents(10, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Lock owner is required");

        verifyNoInteractions(outboxEventRepository);
    }

    private OutboxEvent createOutboxEvent(Long transactionId) {
        UUID eventId = UUID.randomUUID();

        Map<String, Object> payload = Map.of(
                "eventId", eventId.toString(),
                "eventVersion", 1,
                "transactionId", transactionId,
                "userId", 7L,
                "occurredAt", "2026-08-05T16:30:00Z"
        );

        return OutboxEvent.create(
                eventId,
                "FINANCIAL_TRANSACTION",
                transactionId,
                "TRANSACTION_CREATED",
                1,
                payload
        );
    }
}