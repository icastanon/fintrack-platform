package com.fintrack.apiservice.outbox.service;

import com.fintrack.apiservice.outbox.entity.OutboxEvent;
import com.fintrack.apiservice.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class OutboxEventLifecycleService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventLifecycleService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(Long outboxEventId, String lockOwner) {
        OutboxEvent event = getForUpdate(outboxEventId);
        event.markPublished(lockOwner, Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPublicationFailure(Long outboxEventId, String lockOwner, String error, int maxAttempts, Duration retryDelay) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("Maximum attempts must be positive");
        }

        if (retryDelay == null || retryDelay.isNegative()) {
            throw new IllegalArgumentException("Retry delay cannot be negative");
        }

        OutboxEvent event = getForUpdate(outboxEventId);

        if (event.getAttemptCount() >= maxAttempts) {
            event.markFailed(lockOwner, error);
            return;
        }

        event.rescheduleAfterFailure(lockOwner, Instant.now().plus(retryDelay), error);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStaleClaims(Duration claimTimeout, int batchSize) {
        if (claimTimeout == null || claimTimeout.isZero() || claimTimeout.isNegative()) {
            throw new IllegalArgumentException("Claim timeout must be positive");
        }

        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be positive");
        }

        Instant now = Instant.now();
        Instant staleBefore = now.minus(claimTimeout);

        List<OutboxEvent> staleEvents = outboxEventRepository.findStaleProcessingForUpdate(staleBefore, batchSize);
        staleEvents.forEach(event -> event.recoverStaleClaim(now));

        return staleEvents.size();
    }

    private OutboxEvent getForUpdate(Long outboxEventId) {
        return outboxEventRepository.findByIdForUpdate(outboxEventId)
                .orElseThrow(() -> new IllegalStateException("Outbox event was not found: " + outboxEventId));
    }
}