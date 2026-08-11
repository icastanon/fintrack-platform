package com.fintrack.workerservice.transactionimport.exception;

public class TransactionImportNotFoundException extends RuntimeException {

    public TransactionImportNotFoundException(Long importId, Long accountId, Long userId) {
        super(
                "Transaction import " + importId
                        + " was not found for account " + accountId
                        + " and user " + userId
        );
    }
}