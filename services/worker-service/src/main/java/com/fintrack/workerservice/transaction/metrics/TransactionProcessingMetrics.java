package com.fintrack.workerservice.transaction.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TransactionProcessingMetrics {

    private static final String MESSAGE_METRIC = "fintrack.transaction.processing.messages";

    private final Counter processedCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;
    private final Counter unsupportedVersionCounter;

    public TransactionProcessingMetrics(MeterRegistry meterRegistry) {
        this.processedCounter = createCounter(meterRegistry, "processed");
        this.duplicateCounter = createCounter(meterRegistry, "duplicate");
        this.failedCounter = createCounter(meterRegistry, "failed");
        this.unsupportedVersionCounter = createCounter(meterRegistry, "unsupported_version");
    }

    public void recordProcessed() {
        processedCounter.increment();
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

    private Counter createCounter(MeterRegistry meterRegistry, String outcome) {
        return Counter.builder(MESSAGE_METRIC)
                .description("Number of transaction-processing messages by outcome")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }
}