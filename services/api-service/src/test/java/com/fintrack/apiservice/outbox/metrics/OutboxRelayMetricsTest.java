package com.fintrack.apiservice.outbox.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRelayMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private OutboxRelayMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new OutboxRelayMetrics(meterRegistry);
    }

    @Test
    void recordsOutboxEventOutcomes() {
        metrics.recordPublished();
        metrics.recordPublished();
        metrics.recordRetryScheduled();
        metrics.recordPermanentlyFailed();

        assertThat(eventCount("published")).isEqualTo(2.0);
        assertThat(eventCount("retry_scheduled")).isEqualTo(1.0);
        assertThat(eventCount("permanently_failed")).isEqualTo(1.0);
    }

    @Test
    void recordsRecoveredEventsByCount() {
        metrics.recordStaleRecovered(3);
        metrics.recordStaleRecovered(0);

        assertThat(eventCount("stale_recovered")).isEqualTo(3.0);
    }

    @Test
    void recordsRelayCycleFailures() {
        metrics.recordRelayFailure();
        metrics.recordRelayFailure();

        double failureCount = meterRegistry
                .get("fintrack.outbox.relay.failures")
                .counter()
                .count();

        assertThat(failureCount).isEqualTo(2.0);
    }

    private double eventCount(String outcome) {
        return meterRegistry
                .get("fintrack.outbox.events")
                .tag("outcome", outcome)
                .counter()
                .count();
    }
}