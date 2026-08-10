package com.fintrack.apiservice.transactionimport.exception;

public class TransactionImportStorageException extends RuntimeException {

    public TransactionImportStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}