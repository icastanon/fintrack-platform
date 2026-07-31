package com.fintrack.apiservice.account.dto;

import com.fintrack.apiservice.account.entity.AccountStatus;
import com.fintrack.apiservice.account.entity.AccountType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class FinancialAccountResponse {

    private Long id;
    private String name;
    private AccountType accountType;
    private String currency;
    private BigDecimal openingBalance;
    private BigDecimal currentBalance;
    private AccountStatus status;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
}