package com.fintrack.apiservice.outbox.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelayMetrics {

    private static final String EVENT_METRIC = "fintrack.outbox.events";
    private static final String RELAY_FAILURE_METRIC = "fintrack.outbox.relay.failures";

    private final Counter publishedCounter;
    private final Counter retryScheduledCounter;
    private final Counter permanentlyFailedCounter;
    private final Counter staleRecoveredCounter;
    private final Counter relayFailureCounter;

    public OutboxRelayMetrics(MeterRegistry meterRegistry) {
        this.publishedCounter = createEventCounter(meterRegistry, "published");
        this.retryScheduledCounter = createEventCounter(meterRegistry, "retry_scheduled");
        this.permanentlyFailedCounter = createEventCounter(meterRegistry, "permanently_failed");
        this.staleRecoveredCounter = createEventCounter(meterRegistry, "stale_recovered");
        this.relayFailureCounter = Counter.builder(RELAY_FAILURE_METRIC)
                .description("Number of failed outbox relay cycles")
                .register(meterRegistry);
    }

    public void recordPublished() {
        publishedCounter.increment();
    }

    public void recordRetryScheduled() {
        retryScheduledCounter.increment();
    }

    public void recordPermanentlyFailed() {
        permanentlyFailedCounter.increment();
    }

    public void recordStaleRecovered(int recoveredCount) {
        if (recoveredCount > 0) {
            staleRecoveredCounter.increment(recoveredCount);
        }
    }

    public void recordRelayFailure() {
        relayFailureCounter.increment();
    }

    private Counter createEventCounter(MeterRegistry meterRegistry, String outcome) {
        return Counter.builder(EVENT_METRIC)
                .description("Number of outbox events by lifecycle outcome")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }
}