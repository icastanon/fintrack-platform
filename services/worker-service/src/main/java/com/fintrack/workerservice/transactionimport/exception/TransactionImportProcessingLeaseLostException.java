package com.fintrack.workerservice.transactionimport.exception;

public class TransactionImportProcessingLeaseLostException extends RuntimeException {

    public TransactionImportProcessingLeaseLostException(Long importId,
                                                         String processingOwner,
                                                         long fencingToken) {
        super(
                "Transaction import processing lease is no longer active: importId="
                        + importId
                        + ", processingOwner="
                        + processingOwner
                        + ", fencingToken="
                        + fencingToken
        );
    }
}