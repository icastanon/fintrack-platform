package com.fintrack.workerservice.category.cache.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class CategorizationRuleCacheSnapshot {

    private final Long fallbackCategoryId;
    private final List<CachedCategorizationRule> rules;

    @JsonCreator
    public CategorizationRuleCacheSnapshot(
            @JsonProperty("fallbackCategoryId") Long fallbackCategoryId,
            @JsonProperty("rules") List<CachedCategorizationRule> rules
    ) {
        this.fallbackCategoryId = fallbackCategoryId;
        this.rules = List.copyOf(rules);
    }
}