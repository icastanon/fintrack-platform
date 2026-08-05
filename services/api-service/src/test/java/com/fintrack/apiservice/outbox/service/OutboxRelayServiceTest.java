package com.fintrack.apiservice.outbox.service;

import com.fintrack.apiservice.outbox.entity.OutboxEvent;
import com.fintrack.apiservice.outbox.publisher.OutboxEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    private static final String LOCK_OWNER = "api-instance-1";
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    @Mock
    private OutboxEventClaimService claimService;

    @Mock
    private OutboxEventLifecycleService lifecycleService;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private OutboxRelayService outboxRelayService;

    @Test
    void relayAvailableEventsPublishesAndCompletesEveryClaimedEvent() {
        OutboxEvent firstEvent = mockEvent(51L);
        OutboxEvent secondEvent = mockEvent(52L);

        when(claimService.claimAvailableEvents(10, LOCK_OWNER)).thenReturn(List.of(firstEvent, secondEvent));

        int claimedCount = outboxRelayService.relayAvailableEvents(10, LOCK_OWNER, MAX_ATTEMPTS, RETRY_DELAY);

        assertThat(claimedCount).isEqualTo(2);

        InOrder firstEventOrder = inOrder(outboxEventPublisher, lifecycleService);
        firstEventOrder.verify(outboxEventPublisher).publish(firstEvent);
        firstEventOrder.verify(lifecycleService).markPublished(51L, LOCK_OWNER);

        InOrder secondEventOrder = inOrder(outboxEventPublisher, lifecycleService);
        secondEventOrder.verify(outboxEventPublisher).publish(secondEvent);
        secondEventOrder.verify(lifecycleService).markPublished(52L, LOCK_OWNER);

        verify(lifecycleService, never()).recordPublicationFailure(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );
    }

    @Test
    void relayAvailableEventsRecordsFailureAndContinuesWithRemainingEvents() {
        OutboxEvent failedEvent = mockEvent(51L);
        OutboxEvent successfulEvent = mockEvent(52L);

        when(claimService.claimAvailableEvents(10, LOCK_OWNER)).thenReturn(List.of(failedEvent, successfulEvent));
        doThrow(new IllegalStateException("SQS unavailable")).when(outboxEventPublisher).publish(failedEvent);

        int claimedCount = outboxRelayService.relayAvailableEvents(10, LOCK_OWNER, MAX_ATTEMPTS, RETRY_DELAY);

        assertThat(claimedCount).isEqualTo(2);

        verify(lifecycleService).recordPublicationFailure(
                51L,
                LOCK_OWNER,
                "SQS unavailable",
                MAX_ATTEMPTS,
                RETRY_DELAY
        );
        verify(lifecycleService, never()).markPublished(51L, LOCK_OWNER);

        verify(outboxEventPublisher).publish(successfulEvent);
        verify(lifecycleService).markPublished(52L, LOCK_OWNER);
    }

    @Test
    void relayAvailableEventsDoesNothingWhenNoEventsAreAvailable() {
        when(claimService.claimAvailableEvents(10, LOCK_OWNER)).thenReturn(List.of());

        int claimedCount = outboxRelayService.relayAvailableEvents(10, LOCK_OWNER, MAX_ATTEMPTS, RETRY_DELAY);

        assertThat(claimedCount).isZero();

        verifyNoInteractions(outboxEventPublisher);
        verifyNoInteractions(lifecycleService);
    }

    @Test
    void relayAvailableEventsDoesNotRecordPublicationFailureWhenMessageWasSentButMarkPublishedFails() {
        OutboxEvent event = mockEvent(51L);

        when(claimService.claimAvailableEvents(10, LOCK_OWNER)).thenReturn(List.of(event));
        doThrow(new IllegalStateException("Database unavailable")).when(lifecycleService).markPublished(51L, LOCK_OWNER);

        assertThatThrownBy(() -> outboxRelayService.relayAvailableEvents(10, LOCK_OWNER, MAX_ATTEMPTS, RETRY_DELAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database unavailable");

        verify(outboxEventPublisher).publish(event);
        verify(lifecycleService, never()).recordPublicationFailure(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );
    }

    private OutboxEvent mockEvent(Long id) {
        OutboxEvent event = mock(OutboxEvent.class);
        when(event.getId()).thenReturn(id);
        return event;
    }
}