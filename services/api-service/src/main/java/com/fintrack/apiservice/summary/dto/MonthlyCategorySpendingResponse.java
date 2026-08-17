package com.fintrack.apiservice.summary.dto;

import com.fintrack.apiservice.user.entity.SupportedCurrency;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Getter
@AllArgsConstructor
public class MonthlyCategorySpendingResponse {

    private final YearMonth month;
    private final BigDecimal totalExpenses;
    private final SupportedCurrency currency;
    private final List<CategorySpendingItemResponse> categories;
}