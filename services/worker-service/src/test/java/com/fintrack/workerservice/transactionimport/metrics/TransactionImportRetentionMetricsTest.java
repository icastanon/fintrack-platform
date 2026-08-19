package com.fintrack.workerservice.transactionimport.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionImportRetentionMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private TransactionImportRetentionMetrics retentionMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        retentionMetrics = new TransactionImportRetentionMetrics(meterRegistry);
    }

    @AfterEach
    void closeMeterRegistry() {
        meterRegistry.close();
    }

    @Test
    void recordsRetentionOutcomes() {
        retentionMetrics.recordAbandonedImports(2);
        retentionMetrics.recordDeletedStagingRows(7);
        retentionMetrics.recordFailure();

        assertThat(counter("fintrack.transaction.import.retention.abandoned")).isEqualTo(2.0);
        assertThat(counter("fintrack.transaction.import.retention.staging.deleted")).isEqualTo(7.0);
        assertThat(counter("fintrack.transaction.import.retention.failures")).isEqualTo(1.0);
    }

    private double counter(String metricName) {
        return meterRegistry.get(metricName).counter().count();
    }
}