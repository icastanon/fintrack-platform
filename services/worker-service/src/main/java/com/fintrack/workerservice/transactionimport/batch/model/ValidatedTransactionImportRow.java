package com.fintrack.workerservice.transactionimport.batch.model;

import com.fintrack.workerservice.transaction.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class ValidatedTransactionImportRow {

    private final int rowNumber;
    private final LocalDate transactionDate;
    private final TransactionType transactionType;
    private final BigDecimal amount;
    private final String merchant;
    private final String description;
    private final Long categoryId;
}