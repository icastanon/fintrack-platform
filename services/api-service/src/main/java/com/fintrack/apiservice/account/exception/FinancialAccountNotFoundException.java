package com.fintrack.apiservice.account.exception;

public class FinancialAccountNotFoundException extends RuntimeException {

    public FinancialAccountNotFoundException() {
        super("Financial account not found");
    }
}