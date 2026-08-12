package com.fintrack.workerservice.transactionimport.exception;

public class TransactionImportRequestMismatchException extends RuntimeException {

    public TransactionImportRequestMismatchException(Long importId) {
        super("Transaction import request does not match authoritative import " + importId);
    }
}