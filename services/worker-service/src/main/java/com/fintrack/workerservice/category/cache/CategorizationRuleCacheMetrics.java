package com.fintrack.workerservice.category.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CategorizationRuleCacheMetrics {

    private static final String REQUEST_METRIC = "fintrack.categorization.rules.cache.requests";
    private static final String ERROR_METRIC = "fintrack.categorization.rules.cache.errors";

    private final Counter hitCounter;
    private final Counter missCounter;
    private final Counter redisReadErrorCounter;
    private final Counter redisWriteErrorCounter;
    private final Counter deserializationErrorCounter;
    private final Counter serializationErrorCounter;
    private final Counter evictionErrorCounter;

    public CategorizationRuleCacheMetrics(MeterRegistry meterRegistry) {
        this.hitCounter = createRequestCounter(meterRegistry, "hit");
        this.missCounter = createRequestCounter(meterRegistry, "miss");
        this.redisReadErrorCounter = createErrorCounter(meterRegistry, "redis_read");
        this.redisWriteErrorCounter = createErrorCounter(meterRegistry, "redis_write");
        this.deserializationErrorCounter = createErrorCounter(meterRegistry, "deserialization");
        this.serializationErrorCounter = createErrorCounter(meterRegistry, "serialization");
        this.evictionErrorCounter = createErrorCounter(meterRegistry, "eviction");
    }

    public void recordHit() {
        hitCounter.increment();
    }

    public void recordMiss() {
        missCounter.increment();
    }

    public void recordRedisReadError() {
        redisReadErrorCounter.increment();
    }

    public void recordRedisWriteError() {
        redisWriteErrorCounter.increment();
    }

    public void recordDeserializationError() {
        deserializationErrorCounter.increment();
    }

    public void recordSerializationError() {
        serializationErrorCounter.increment();
    }

    public void recordEvictionError() {
        evictionErrorCounter.increment();
    }

    private Counter createRequestCounter(MeterRegistry meterRegistry, String outcome) {
        return Counter.builder(REQUEST_METRIC)
                .description("Categorization-rule cache requests")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    private Counter createErrorCounter(MeterRegistry meterRegistry, String operation) {
        return Counter.builder(ERROR_METRIC)
                .description("Categorization-rule cache errors")
                .tag("operation", operation)
                .register(meterRegistry);
    }
}