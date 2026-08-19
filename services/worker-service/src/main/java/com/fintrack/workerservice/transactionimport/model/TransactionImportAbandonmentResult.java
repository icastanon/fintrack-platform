package com.fintrack.workerservice.transactionimport.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransactionImportAbandonmentResult {

    private final int abandonedImportCount;
    private final int deletedRejectedRowCount;
}