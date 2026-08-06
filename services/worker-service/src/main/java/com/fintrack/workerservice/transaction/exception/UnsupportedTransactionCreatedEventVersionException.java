package com.fintrack.workerservice.transaction.exception;

import com.fintrack.eventcontracts.TransactionCreatedEvent;

public class UnsupportedTransactionCreatedEventVersionException extends RuntimeException {

    public UnsupportedTransactionCreatedEventVersionException(int receivedVersion) {
        super(
                "Unsupported transaction-created event version: "
                        + receivedVersion
                        + ". Supported version: "
                        + TransactionCreatedEvent.CURRENT_VERSION
        );
    }
}