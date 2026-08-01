package com.fintrack.apiservice.account.exception;

public class FinancialAccountVersionConflictException
        extends RuntimeException {

    public FinancialAccountVersionConflictException() {
        super("The financial account was modified by another request. Reload the account and try again.");
    }
}