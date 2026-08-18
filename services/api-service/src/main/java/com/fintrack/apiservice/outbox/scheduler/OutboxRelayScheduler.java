package com.fintrack.apiservice.outbox.scheduler;

import com.fintrack.apiservice.outbox.metrics.OutboxRelayMetrics;
import com.fintrack.apiservice.outbox.service.OutboxEventLifecycleService;
import com.fintrack.apiservice.outbox.service.OutboxRelayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "fintrack.outbox.relay.enabled", havingValue = "true")
public class OutboxRelayScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRelayService outboxRelayService;
    private final OutboxEventLifecycleService lifecycleService;
    private final OutboxRelayMetrics outboxRelayMetrics;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration retryDelay;
    private final Duration claimTimeout;
    private final String lockOwner;

    public OutboxRelayScheduler(OutboxRelayService outboxRelayService,
                                OutboxEventLifecycleService lifecycleService,
                                OutboxRelayMetrics outboxRelayMetrics,
                                @Value("${fintrack.outbox.relay.batch-size}") int batchSize,
                                @Value("${fintrack.outbox.relay.max-attempts}") int maxAttempts,
                                @Value("${fintrack.outbox.relay.retry-delay}") Duration retryDelay,
                                @Value("${fintrack.outbox.relay.claim-timeout}") Duration claimTimeout) {
        this.outboxRelayService = outboxRelayService;
        this.lifecycleService = lifecycleService;
        this.outboxRelayMetrics = outboxRelayMetrics;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
        this.claimTimeout = claimTimeout;
        this.lockOwner = "api-service-" + UUID.randomUUID();
    }

    @Scheduled(fixedDelayString = "${fintrack.outbox.relay.fixed-delay-ms}")
    public void runRelayCycle() {
        try {
            int recoveredCount = lifecycleService.recoverStaleClaims(claimTimeout, batchSize);
            outboxRelayMetrics.recordStaleRecovered(recoveredCount);

            int claimedCount = outboxRelayService.relayAvailableEvents(batchSize, lockOwner, maxAttempts, retryDelay);

            if (recoveredCount > 0 || claimedCount > 0) {
                LOGGER.info(
                        "Completed outbox relay cycle: lockOwner={}, recoveredCount={}, claimedCount={}",
                        lockOwner,
                        recoveredCount,
                        claimedCount
                );
            }
        } catch (Exception exception) {
            outboxRelayMetrics.recordRelayFailure();
            LOGGER.error("Outbox relay cycle failed: lockOwner={}", lockOwner, exception);
        }
    }
}