package com.fintrack.apiservice.transactionimport.exception;

public class TransactionImportNotFoundException extends RuntimeException {

    public TransactionImportNotFoundException() {
        super("Transaction import was not found");
    }
}