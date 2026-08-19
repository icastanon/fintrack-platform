package com.fintrack.workerservice.transactionimport.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TransactionImportMetrics {

    private static final String MESSAGE_METRIC = "fintrack.transaction.import.messages";
    private static final String LEASE_METRIC = "fintrack.transaction.import.leases";

    private final Counter completedCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;
    private final Counter unsupportedVersionCounter;
    private final Counter leaseAcquiredCounter;
    private final Counter activeLeaseCounter;
    private final Counter alreadyCompletedLeaseCounter;
    private final Counter lostLeaseCounter;
    private final Counter abandonedCounter;
    private final Counter alreadyAbandonedLeaseCounter;

    public TransactionImportMetrics(MeterRegistry meterRegistry) {
        this.completedCounter = createMessageCounter(meterRegistry, "completed");
        this.duplicateCounter = createMessageCounter(meterRegistry, "duplicate");
        this.failedCounter = createMessageCounter(meterRegistry, "failed");
        this.unsupportedVersionCounter = createMessageCounter(meterRegistry, "unsupported_version");
        this.leaseAcquiredCounter = createLeaseCounter(meterRegistry, "acquired");
        this.activeLeaseCounter = createLeaseCounter(meterRegistry, "active");
        this.alreadyCompletedLeaseCounter = createLeaseCounter(meterRegistry, "already_completed");
        this.lostLeaseCounter = createLeaseCounter(meterRegistry, "lost");
        this.abandonedCounter = createMessageCounter(meterRegistry, "abandoned");
        this.alreadyAbandonedLeaseCounter = createLeaseCounter(meterRegistry, "already_abandoned");
    }

    public void recordCompleted() {
        completedCounter.increment();
    }

    public void recordDuplicate() {
        duplicateCounter.increment();
    }

    public void recordFailed() {
        failedCounter.increment();
    }

    public void recordUnsupportedVersion() {
        unsupportedVersionCounter.increment();
    }

    public void recordLeaseAcquired() {
        leaseAcquiredCounter.increment();
    }

    public void recordActiveLease() {
        activeLeaseCounter.increment();
    }

    public void recordAlreadyCompletedLease() {
        alreadyCompletedLeaseCounter.increment();
    }

    public void recordLostLease() {
        lostLeaseCounter.increment();
    }

    public void recordAbandoned() {
        abandonedCounter.increment();
    }

    public void recordAlreadyAbandonedLease() {
        alreadyAbandonedLeaseCounter.increment();
    }

    private Counter createMessageCounter(MeterRegistry meterRegistry, String outcome) {
        return Counter.builder(MESSAGE_METRIC)
                .description("Number of transaction-import messages by outcome")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    private Counter createLeaseCounter(MeterRegistry meterRegistry, String outcome) {
        return Counter.builder(LEASE_METRIC)
                .description("Number of transaction-import processing lease outcomes")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }
}