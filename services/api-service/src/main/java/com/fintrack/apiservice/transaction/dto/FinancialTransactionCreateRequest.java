package com.fintrack.apiservice.transaction.dto;

import com.fintrack.apiservice.transaction.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FinancialTransactionCreateRequest {

    @NotNull(message = "Account ID is required")
    @Positive(message = "Account ID must be positive")
    private Long accountId;

    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 integer digits and 2 decimal places")
    private BigDecimal amount;

    @Size(max = 200, message = "Merchant must not exceed 200 characters")
    @Pattern(regexp = "(?s).*\\S.*", message = "Merchant must not be blank")
    private String merchant;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Pattern(regexp = "(?s).*\\S.*", message = "Description must not be blank")
    private String description;

    @NotNull(message = "Transaction date is required")
    @PastOrPresent(message = "Transaction date cannot be in the future")
    private LocalDate transactionDate;
}