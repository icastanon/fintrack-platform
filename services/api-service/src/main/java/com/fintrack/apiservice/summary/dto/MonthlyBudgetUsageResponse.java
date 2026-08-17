package com.fintrack.apiservice.summary.dto;

import com.fintrack.apiservice.user.entity.SupportedCurrency;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.YearMonth;
import java.util.List;

@Getter
@AllArgsConstructor
public class MonthlyBudgetUsageResponse {

    private final YearMonth month;
    private final SupportedCurrency currency;
    private final List<BudgetUsageItemResponse> budgets;
}