package com.fintrack.workerservice.transaction.exception;

public class FinancialTransactionNotFoundException extends RuntimeException {

    public FinancialTransactionNotFoundException(Long transactionId, Long userId) {
        super("Financial transaction " + transactionId + " was not found for user " + userId);
    }
}