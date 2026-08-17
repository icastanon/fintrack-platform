package com.fintrack.apiservice.summary.dto;

import com.fintrack.apiservice.user.entity.SupportedCurrency;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.YearMonth;

@Getter
@AllArgsConstructor
public class MonthlyCashFlowResponse {

    private final YearMonth month;
    private final BigDecimal income;
    private final BigDecimal expenses;
    private final BigDecimal netCashFlow;
    private final SupportedCurrency currency;
}