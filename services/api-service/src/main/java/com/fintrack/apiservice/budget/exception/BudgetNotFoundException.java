package com.fintrack.apiservice.budget.exception;

public class BudgetNotFoundException extends RuntimeException {

    public BudgetNotFoundException() {
        super("Budget was not found");
    }
}