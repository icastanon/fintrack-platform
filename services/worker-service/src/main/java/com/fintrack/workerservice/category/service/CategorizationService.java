package com.fintrack.workerservice.category.service;

import com.fintrack.workerservice.category.cache.CategorizationRuleCache;
import com.fintrack.workerservice.category.cache.model.CachedCategorizationRule;
import com.fintrack.workerservice.category.cache.model.CategorizationRuleCacheSnapshot;
import com.fintrack.workerservice.category.util.MerchantNormalizer;
import org.springframework.stereotype.Service;

@Service
public class CategorizationService {

    private final CategorizationRuleCache categorizationRuleCache;

    public CategorizationService(CategorizationRuleCache categorizationRuleCache) {
        this.categorizationRuleCache = categorizationRuleCache;
    }

    public Long categorizeMerchant(String merchant) {
        CategorizationRuleCacheSnapshot snapshot = categorizationRuleCache.getSnapshot();
        String normalizedMerchant = MerchantNormalizer.normalize(merchant);

        return snapshot.getRules()
                .stream()
                .filter(rule -> matches(normalizedMerchant, rule))
                .map(CachedCategorizationRule::getCategoryId)
                .findFirst()
                .orElse(snapshot.getFallbackCategoryId());
    }

    private boolean matches(String normalizedMerchant, CachedCategorizationRule rule) {
        if (normalizedMerchant.isBlank()) {
            return false;
        }

        String normalizedPattern = rule.getNormalizedMerchantPattern();

        return !normalizedPattern.isBlank() && normalizedMerchant.contains(normalizedPattern);
    }
}