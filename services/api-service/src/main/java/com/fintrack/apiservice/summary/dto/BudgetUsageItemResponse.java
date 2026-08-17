package com.fintrack.apiservice.summary.dto;

import com.fintrack.apiservice.summary.model.BudgetStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class BudgetUsageItemResponse {

    private final Long budgetId;
    private final Long categoryId;
    private final String categoryName;
    private final BigDecimal budgetAmount;
    private final Integer warningThresholdPercentage;
    private final BigDecimal spentAmount;
    private final BigDecimal remainingAmount;
    private final BigDecimal usagePercentage;
    private final BudgetStatus status;
}