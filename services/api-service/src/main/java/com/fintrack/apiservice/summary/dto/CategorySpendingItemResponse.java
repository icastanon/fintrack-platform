package com.fintrack.apiservice.summary.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class CategorySpendingItemResponse {

    private final Long categoryId;
    private final String categoryName;
    private final BigDecimal spentAmount;
}