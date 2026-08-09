package com.fintrack.workerservice.budget.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class BudgetEvaluationResult {

    private final Long budgetId;
    private final BigDecimal budgetAmount;
    private final BigDecimal spentAmount;
    private final BigDecimal usagePercentage;
    private final BudgetStatus status;
}