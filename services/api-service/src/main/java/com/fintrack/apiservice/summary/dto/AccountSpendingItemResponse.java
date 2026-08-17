package com.fintrack.apiservice.summary.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class AccountSpendingItemResponse {

    private final Long accountId;
    private final String accountName;
    private final BigDecimal spentAmount;
}