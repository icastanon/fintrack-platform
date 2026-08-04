package com.fintrack.apiservice.transaction.exception;

public class FinancialTransactionNotFoundException extends RuntimeException {

    public FinancialTransactionNotFoundException() {
        super("Financial transaction was not found");
    }
}