package com.fintrack.workerservice.transaction.exception;

import com.fintrack.eventcontracts.TransactionProcessingRequestEvent;

public class UnsupportedTransactionProcessingRequestEventVersionException extends RuntimeException {

    public UnsupportedTransactionProcessingRequestEventVersionException(int receivedVersion) {
        super(
                "Unsupported transaction-processing request event version: "
                        + receivedVersion
                        + ". Supported version: "
                        + TransactionProcessingRequestEvent.CURRENT_VERSION
        );
    }
}