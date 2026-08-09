package com.fintrack.workerservice.category.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategorizationRuleCacheMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private CategorizationRuleCacheMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new CategorizationRuleCacheMetrics(meterRegistry);
    }

    @Test
    void recordsCacheHitsAndMisses() {
        metrics.recordHit();
        metrics.recordHit();
        metrics.recordMiss();

        assertThat(requestCount("hit")).isEqualTo(2.0);
        assertThat(requestCount("miss")).isEqualTo(1.0);
    }

    @Test
    void recordsCacheErrorsByOperation() {
        metrics.recordRedisReadError();
        metrics.recordRedisWriteError();
        metrics.recordDeserializationError();
        metrics.recordSerializationError();
        metrics.recordEvictionError();

        assertThat(errorCount("redis_read")).isEqualTo(1.0);
        assertThat(errorCount("redis_write")).isEqualTo(1.0);
        assertThat(errorCount("deserialization")).isEqualTo(1.0);
        assertThat(errorCount("serialization")).isEqualTo(1.0);
        assertThat(errorCount("eviction")).isEqualTo(1.0);
    }

    private double requestCount(String outcome) {
        return meterRegistry
                .get("fintrack.categorization.rules.cache.requests")
                .tag("outcome", outcome)
                .counter()
                .count();
    }

    private double errorCount(String operation) {
        return meterRegistry
                .get("fintrack.categorization.rules.cache.errors")
                .tag("operation", operation)
                .counter()
                .count();
    }
}