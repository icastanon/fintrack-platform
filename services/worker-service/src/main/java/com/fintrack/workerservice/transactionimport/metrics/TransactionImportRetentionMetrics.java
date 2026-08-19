package com.fintrack.workerservice.transactionimport.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TransactionImportRetentionMetrics {

    private static final String ABANDONED_IMPORT_METRIC = "fintrack.transaction.import.retention.abandoned";
    private static final String DELETED_STAGING_ROW_METRIC = "fintrack.transaction.import.retention.staging.deleted";
    private static final String FAILURE_METRIC = "fintrack.transaction.import.retention.failures";

    private final Counter abandonedImportCounter;
    private final Counter deletedStagingRowCounter;
    private final Counter failureCounter;

    public TransactionImportRetentionMetrics(MeterRegistry meterRegistry) {
        this.abandonedImportCounter = Counter.builder(ABANDONED_IMPORT_METRIC)
                .description("Number of stale failed imports marked abandoned")
                .register(meterRegistry);

        this.deletedStagingRowCounter = Counter.builder(DELETED_STAGING_ROW_METRIC)
                .description("Number of rejected-row staging records deleted during abandonment")
                .register(meterRegistry);

        this.failureCounter = Counter.builder(FAILURE_METRIC)
                .description("Number of failed import-retention executions")
                .register(meterRegistry);
    }

    public void recordAbandonedImports(int count) {
        if (count > 0) {
            abandonedImportCounter.increment(count);
        }
    }

    public void recordDeletedStagingRows(int count) {
        if (count > 0) {
            deletedStagingRowCounter.increment(count);
        }
    }

    public void recordFailure() {
        failureCounter.increment();
    }
}