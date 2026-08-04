package com.fintrack.apiservice.budget.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

@Getter
@AllArgsConstructor
public class BudgetResponse {

    private final Long id;
    private final Long categoryId;
    private final String categoryName;
    private final YearMonth budgetMonth;
    private final BigDecimal amount;
    private final Integer warningThresholdPercentage;
    private final Long version;
    private final Instant createdAt;
    private final Instant updatedAt;
}