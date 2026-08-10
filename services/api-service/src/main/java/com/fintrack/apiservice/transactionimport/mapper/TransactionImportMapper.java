package com.fintrack.apiservice.transactionimport.mapper;

import com.fintrack.apiservice.transactionimport.dto.TransactionImportResponse;
import com.fintrack.apiservice.transactionimport.entity.TransactionImport;
import org.springframework.stereotype.Component;

@Component
public class TransactionImportMapper {

    public TransactionImportResponse toResponse(TransactionImport transactionImport) {
        return new TransactionImportResponse(
                transactionImport.getId(),
                transactionImport.getAccount().getId(),
                transactionImport.getAccount().getName(),
                transactionImport.getOriginalFileName(),
                transactionImport.getContentType(),
                transactionImport.getFileSizeBytes(),
                transactionImport.getStatus(),
                transactionImport.getTotalRows(),
                transactionImport.getProcessedRows(),
                transactionImport.getSuccessfulRows(),
                transactionImport.getSkippedRows(),
                transactionImport.getFailedRows(),
                transactionImport.getFailureSummary(),
                transactionImport.getRejectedObjectKey() != null,
                transactionImport.getVersion(),
                transactionImport.getStartedAt(),
                transactionImport.getCompletedAt(),
                transactionImport.getCreatedAt(),
                transactionImport.getUpdatedAt()
        );
    }
}