package com.fintrack.workerservice.transactionimport.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionImportMetricsTest {

    private static final String MESSAGE_METRIC = "fintrack.transaction.import.messages";
    private static final String LEASE_METRIC = "fintrack.transaction.import.leases";

    private SimpleMeterRegistry meterRegistry;
    private TransactionImportMetrics transactionImportMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        transactionImportMetrics = new TransactionImportMetrics(meterRegistry);
    }

    @AfterEach
    void closeMeterRegistry() {
        meterRegistry.close();
    }

    @Test
    void recordsMessageOutcomes() {
        transactionImportMetrics.recordCompleted();
        transactionImportMetrics.recordDuplicate();
        transactionImportMetrics.recordFailed();
        transactionImportMetrics.recordUnsupportedVersion();
        transactionImportMetrics.recordAbandoned();

        assertThat(messageCount("completed")).isEqualTo(1.0);
        assertThat(messageCount("duplicate")).isEqualTo(1.0);
        assertThat(messageCount("failed")).isEqualTo(1.0);
        assertThat(messageCount("unsupported_version")).isEqualTo(1.0);
        assertThat(messageCount("abandoned")).isEqualTo(1.0);
    }

    @Test
    void recordsLeaseOutcomes() {
        transactionImportMetrics.recordLeaseAcquired();
        transactionImportMetrics.recordActiveLease();
        transactionImportMetrics.recordAlreadyCompletedLease();
        transactionImportMetrics.recordLostLease();
        transactionImportMetrics.recordAlreadyAbandonedLease();

        assertThat(leaseCount("acquired")).isEqualTo(1.0);
        assertThat(leaseCount("active")).isEqualTo(1.0);
        assertThat(leaseCount("already_completed")).isEqualTo(1.0);
        assertThat(leaseCount("lost")).isEqualTo(1.0);
        assertThat(leaseCount("already_abandoned")).isEqualTo(1.0);
    }

    private double messageCount(String outcome) {
        return meterRegistry.get(MESSAGE_METRIC)
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private double leaseCount(String outcome) {
        return meterRegistry.get(LEASE_METRIC)
                .tag("outcome", outcome)
                .counter()
                .count();
    }
}