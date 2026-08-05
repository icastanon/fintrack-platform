package com.fintrack.apiservice.outbox.service;

import com.fintrack.apiservice.outbox.entity.OutboxEvent;
import com.fintrack.apiservice.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OutboxEventClaimService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxEventClaimService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> claimAvailableEvents(int batchSize, String lockOwner) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be positive");
        }

        if (lockOwner == null || lockOwner.isBlank()) {
            throw new IllegalArgumentException("Lock owner is required");
        }

        List<OutboxEvent> events = outboxEventRepository.findAvailablePendingForUpdate(batchSize);
        Instant lockedAt = Instant.now();

        events.forEach(event -> event.claim(lockOwner, lockedAt));

        return List.copyOf(events);
    }
}