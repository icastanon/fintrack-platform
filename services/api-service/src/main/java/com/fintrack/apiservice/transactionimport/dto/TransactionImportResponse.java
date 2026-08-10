package com.fintrack.apiservice.transactionimport.dto;

import com.fintrack.apiservice.transactionimport.entity.TransactionImportStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class TransactionImportResponse {

    private final Long id;
    private final Long accountId;
    private final String accountName;
    private final String originalFileName;
    private final String contentType;
    private final Long fileSizeBytes;
    private final TransactionImportStatus status;
    private final Long totalRows;
    private final Long processedRows;
    private final Long successfulRows;
    private final Long skippedRows;
    private final Long failedRows;
    private final String failureSummary;
    private final boolean rejectedOutputAvailable;
    private final Long version;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Instant createdAt;
    private final Instant updatedAt;
}