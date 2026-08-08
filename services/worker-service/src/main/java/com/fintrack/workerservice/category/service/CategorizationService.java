package com.fintrack.workerservice.category.service;

import com.fintrack.workerservice.category.entity.CategorizationRule;
import com.fintrack.workerservice.category.repository.CategoryRepository;
import com.fintrack.workerservice.category.repository.CategorizationRuleRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class CategorizationService {

    private static final String FALLBACK_CATEGORY_NAME = "Other";

    private final CategorizationRuleRepository categorizationRuleRepository;
    private final CategoryRepository categoryRepository;

    public CategorizationService(CategorizationRuleRepository categorizationRuleRepository,
                                 CategoryRepository categoryRepository) {
        this.categorizationRuleRepository = categorizationRuleRepository;
        this.categoryRepository = categoryRepository;
    }

    public Long categorizeMerchant(String merchant) {
        String normalizedMerchant = normalize(merchant);

        return categorizationRuleRepository
                .findAllByActiveTrueOrderByPriorityAscIdAsc()
                .stream()
                .filter(rule -> matches(normalizedMerchant, rule))
                .map(CategorizationRule::getCategoryId)
                .findFirst()
                .orElseGet(this::getFallbackCategoryId);
    }

    private boolean matches(String normalizedMerchant, CategorizationRule rule) {
        if (normalizedMerchant.isBlank()) {
            return false;
        }

        String normalizedPattern = normalize(rule.getMerchantPattern());

        return !normalizedPattern.isBlank()
                && normalizedMerchant.contains(normalizedPattern);
    }

    private Long getFallbackCategoryId() {
        return categoryRepository
                .findByNameIgnoreCase(FALLBACK_CATEGORY_NAME)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Required fallback category 'Other' is missing"
                        )
                )
                .getId();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .strip()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }
}