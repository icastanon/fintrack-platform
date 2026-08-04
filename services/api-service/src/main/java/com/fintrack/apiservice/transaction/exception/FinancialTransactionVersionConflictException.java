package com.fintrack.apiservice.transaction.exception;

public class FinancialTransactionVersionConflictException extends RuntimeException {

    public FinancialTransactionVersionConflictException() {
        super("The financial transaction was modified. Reload it and try again.");
    }
}