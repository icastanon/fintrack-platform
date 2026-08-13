package com.fintrack.workerservice.transactionimport.batch.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransactionImportCsvRow {

    private final int rowNumber;
    private final String transactionDate;
    private final String transactionType;
    private final String amount;
    private final String merchant;
    private final String description;
    private final String rawRecord;
}