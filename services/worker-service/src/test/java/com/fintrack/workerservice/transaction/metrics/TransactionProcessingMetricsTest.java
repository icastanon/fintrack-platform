package com.fintrack.workerservice.transaction.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionProcessingMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private TransactionProcessingMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new TransactionProcessingMetrics(meterRegistry);
    }

    @Test
    void recordsTransactionProcessingOutcomes() {
        metrics.recordProcessed();
        metrics.recordProcessed();
        metrics.recordDuplicate();
        metrics.recordFailed();
        metrics.recordUnsupportedVersion();

        assertThat(messageCount("processed")).isEqualTo(2.0);
        assertThat(messageCount("duplicate")).isEqualTo(1.0);
        assertThat(messageCount("failed")).isEqualTo(1.0);
        assertThat(messageCount("unsupported_version")).isEqualTo(1.0);
    }

    private double messageCount(String outcome) {
        return meterRegistry
                .get("fintrack.transaction.processing.messages")
                .tag("outcome", outcome)
                .counter()
                .count();
    }
}