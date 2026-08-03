package com.fintrack.apiservice.category.service;

import com.fintrack.apiservice.category.entity.Category;
import com.fintrack.apiservice.category.entity.CategorizationRule;
import com.fintrack.apiservice.category.repository.CategoryRepository;
import com.fintrack.apiservice.category.repository.CategorizationRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class CategorizationService {

    private static final String FALLBACK_CATEGORY_NAME = "Other";

    private final CategorizationRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;

    public CategorizationService(
            CategorizationRuleRepository ruleRepository,
            CategoryRepository categoryRepository
    ) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
    }

    public Category categorizeMerchant(String merchant) {
        String normalizedMerchant = normalize(merchant);

        return ruleRepository
                .findAllByActiveTrueOrderByPriorityAscIdAsc()
                .stream()
                .filter(rule -> matches(normalizedMerchant, rule))
                .map(CategorizationRule::getCategory)
                .findFirst()
                .orElseGet(this::getFallbackCategory);
    }

    private boolean matches(String normalizedMerchant, CategorizationRule rule) {
        String normalizedPattern = normalize(rule.getMerchantPattern());

        return !normalizedMerchant.isBlank() && normalizedMerchant.contains(normalizedPattern);
    }

    private Category getFallbackCategory() {
        return categoryRepository
                .findByNameIgnoreCase(FALLBACK_CATEGORY_NAME)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Required fallback category 'Other' is missing"
                        )
                );
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}