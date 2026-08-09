package com.fintrack.apiservice.account.dto;

import com.fintrack.apiservice.account.entity.AccountType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FinancialAccountCreateRequest {

    @NotBlank(message = "Account name is required")
    @Size(max = 100, message = "Account name cannot exceed 100 characters")
    private String name;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @NotNull(message = "Opening balance is required")
    @Digits(integer = 17, fraction = 2,
            message = "Opening balance must have at most 17 integer digits and 2 decimal places")
    private BigDecimal openingBalance;
}