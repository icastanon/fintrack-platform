package com.fintrack.apiservice.budget.exception;

public class BudgetVersionConflictException extends RuntimeException {

    public BudgetVersionConflictException() {
        super("Budget was modified by another request. Refresh and try again");
    }
}