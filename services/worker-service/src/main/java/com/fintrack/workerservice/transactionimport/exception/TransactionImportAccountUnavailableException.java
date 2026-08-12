package com.fintrack.workerservice.transactionimport.exception;

public class TransactionImportAccountUnavailableException extends RuntimeException {

    public TransactionImportAccountUnavailableException(Long accountId, Long userId) {
        super("Financial account " + accountId
                + " is unavailable for transaction import by user " + userId);
    }
}