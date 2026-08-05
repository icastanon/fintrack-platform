package com.fintrack.apiservice.outbox.service;

import com.fintrack.apiservice.outbox.entity.OutboxEvent;
import com.fintrack.apiservice.outbox.publisher.OutboxEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class OutboxRelayService {

    private final OutboxEventClaimService claimService;
    private final OutboxEventLifecycleService lifecycleService;
    private final OutboxEventPublisher outboxEventPublisher;

    public OutboxRelayService(OutboxEventClaimService claimService, OutboxEventLifecycleService lifecycleService, OutboxEventPublisher outboxEventPublisher) {
        this.claimService = claimService;
        this.lifecycleService = lifecycleService;
        this.outboxEventPublisher = outboxEventPublisher;
    }

    public int relayAvailableEvents(int batchSize, String lockOwner, int maxAttempts, Duration retryDelay) {
        List<OutboxEvent> claimedEvents = claimService.claimAvailableEvents(batchSize, lockOwner);

        for (OutboxEvent event : claimedEvents) {
            publishClaimedEvent(event, lockOwner, maxAttempts, retryDelay);
        }

        return claimedEvents.size();
    }

    private void publishClaimedEvent(OutboxEvent event, String lockOwner, int maxAttempts, Duration retryDelay) {
        try {
            outboxEventPublisher.publish(event);
        } catch (Exception exception) {
            lifecycleService.recordPublicationFailure(
                    event.getId(),
                    lockOwner,
                    getErrorMessage(exception),
                    maxAttempts,
                    retryDelay
            );
            return;
        }

        lifecycleService.markPublished(event.getId(), lockOwner);
    }

    private String getErrorMessage(Exception exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return exception.getMessage();
    }
}