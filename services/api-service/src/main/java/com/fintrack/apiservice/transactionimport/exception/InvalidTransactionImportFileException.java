package com.fintrack.apiservice.transactionimport.exception;

public class InvalidTransactionImportFileException extends RuntimeException {

    public InvalidTransactionImportFileException(String message) {
        super(message);
    }
}