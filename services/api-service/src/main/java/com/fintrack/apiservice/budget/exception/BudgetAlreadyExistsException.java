package com.fintrack.apiservice.budget.exception;

import java.time.YearMonth;

public class BudgetAlreadyExistsException extends RuntimeException {

    public BudgetAlreadyExistsException(String categoryName, YearMonth budgetMonth) {
        super("A budget already exists for category " + categoryName + " in " + budgetMonth);
    }
}