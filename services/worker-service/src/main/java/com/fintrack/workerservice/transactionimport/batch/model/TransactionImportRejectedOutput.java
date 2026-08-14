package com.fintrack.workerservice.transactionimport.batch.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TransactionImportRejectedOutput {

    private final long rejectedRowCount;
    private final String objectKey;

    public static TransactionImportRejectedOutput none() {
        return new TransactionImportRejectedOutput(0, null);
    }

    public static TransactionImportRejectedOutput uploaded(long rejectedRowCount,
                                                           String objectKey) {
        if (rejectedRowCount <= 0) {
            throw new IllegalArgumentException(
                    "Rejected row count must be positive for an uploaded output"
            );
        }

        Objects.requireNonNull(objectKey, "Rejected output object key is required");

        if (objectKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Rejected output object key cannot be blank"
            );
        }

        return new TransactionImportRejectedOutput(
                rejectedRowCount,
                objectKey
        );
    }

    public boolean exists() {
        return rejectedRowCount > 0;
    }
}