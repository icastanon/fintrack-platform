package com.fintrack.apiservice.transaction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class FinancialTransactionCategoryOverrideRequest {

    @NotNull(message = "Category ID is required")
    @Positive(message = "Category ID must be positive")
    private Long categoryId;

    @NotNull(message = "Version is required")
    @PositiveOrZero(message = "Version cannot be negative")
    private Long version;
}