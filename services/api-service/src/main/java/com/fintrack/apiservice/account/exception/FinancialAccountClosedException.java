package com.fintrack.apiservice.account.exception;

public class FinancialAccountClosedException extends RuntimeException {

    public FinancialAccountClosedException() {
        super("Closed financial accounts cannot be modified");
    }
}