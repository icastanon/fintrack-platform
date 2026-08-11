package com.fintrack.workerservice.transactionimport.exception;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;

public class UnsupportedTransactionImportRequestedEventVersionException extends RuntimeException {

    public UnsupportedTransactionImportRequestedEventVersionException(int receivedVersion) {
        super(
                "Unsupported transaction-import request event version: "
                        + receivedVersion
                        + ". Supported version: "
                        + TransactionImportRequestedEvent.CURRENT_VERSION
        );
    }
}