package com.fintrack.workerservice.category.cache.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class CachedCategorizationRule {

    private final Long id;
    private final String normalizedMerchantPattern;
    private final Long categoryId;
    private final int priority;

    @JsonCreator
    public CachedCategorizationRule(
            @JsonProperty("id") Long id,
            @JsonProperty("normalizedMerchantPattern") String normalizedMerchantPattern,
            @JsonProperty("categoryId") Long categoryId,
            @JsonProperty("priority") int priority
    ) {
        this.id = id;
        this.normalizedMerchantPattern = normalizedMerchantPattern;
        this.categoryId = categoryId;
        this.priority = priority;
    }
}