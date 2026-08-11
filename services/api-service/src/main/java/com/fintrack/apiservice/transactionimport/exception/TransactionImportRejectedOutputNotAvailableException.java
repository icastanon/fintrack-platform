package com.fintrack.apiservice.transactionimport.exception;

public class TransactionImportRejectedOutputNotAvailableException extends RuntimeException {

    public TransactionImportRejectedOutputNotAvailableException() {
        super("Rejected output is not available for this transaction import");
    }
}