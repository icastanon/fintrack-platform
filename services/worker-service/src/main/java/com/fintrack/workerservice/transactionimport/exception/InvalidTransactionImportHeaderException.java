package com.fintrack.workerservice.transactionimport.exception;

public class InvalidTransactionImportHeaderException extends RuntimeException {

    public InvalidTransactionImportHeaderException(String expectedHeader) {
        super("Transaction import CSV header must be: " + expectedHeader);
    }
}