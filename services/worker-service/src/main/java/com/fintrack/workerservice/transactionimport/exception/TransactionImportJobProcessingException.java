package com.fintrack.workerservice.transactionimport.exception;

public class TransactionImportJobProcessingException extends RuntimeException {

    public TransactionImportJobProcessingException(String message) {
        super(message);
    }

    public TransactionImportJobProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}