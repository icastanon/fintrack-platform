package com.fintrack.apiservice.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.YearMonth;

@Data
public class BudgetCreateRequest {

    @NotNull(message = "Category ID is required")
    @Positive(message = "Category ID must be positive")
    private Long categoryId;

    @NotNull(message = "Budget month is required")
    private YearMonth budgetMonth;

    @NotNull(message = "Budget amount is required")
    @DecimalMin(value = "0.01", message = "Budget amount must be at least 0.01")
    @Digits(integer = 17, fraction = 2, message = "Budget amount must have at most 17 integer digits and 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Warning threshold percentage is required")
    @Min(value = 1, message = "Warning threshold percentage must be at least 1")
    @Max(value = 99, message = "Warning threshold percentage cannot exceed 99")
    private Integer warningThresholdPercentage;
}