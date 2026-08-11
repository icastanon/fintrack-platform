package com.fintrack.workerservice.transactionimport.exception;

import lombok.Getter;

@Getter
public class TransactionImportRowValidationException extends RuntimeException {

    private final int rowNumber;

    public TransactionImportRowValidationException(int rowNumber, String reason) {
        super("Row " + rowNumber + ": " + reason);
        this.rowNumber = rowNumber;
    }
}