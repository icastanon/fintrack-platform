package com.fintrack.apiservice.transaction.dto;

import com.fintrack.apiservice.transaction.entity.ProcessingStatus;
import com.fintrack.apiservice.transaction.entity.TransactionSource;
import com.fintrack.apiservice.transaction.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class FinancialTransactionResponse {

    private final Long id;

    private final Long accountId;

    private final String accountName;

    private final Long categoryId;

    private final String categoryName;

    private final TransactionType transactionType;

    private final BigDecimal amount;

    private final String merchant;

    private final String description;

    private final LocalDate transactionDate;

    private final ProcessingStatus processingStatus;

    private final TransactionSource source;

    private final boolean manualCategoryOverride;

    private final Long version;

    private final Instant createdAt;

    private final Instant updatedAt;

    private final Long importId;

    private final Integer importRowNumber;

}