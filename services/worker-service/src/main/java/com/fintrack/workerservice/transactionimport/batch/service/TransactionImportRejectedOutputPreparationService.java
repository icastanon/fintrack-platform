package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportRejectedOutput;
import com.fintrack.workerservice.transactionimport.entity.TransactionImportRejectedRowStaging;
import com.fintrack.workerservice.transactionimport.storage.TransactionImportStorageService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class TransactionImportRejectedOutputPreparationService {

    private final TransactionImportRejectedRowStagingService rejectedRowStagingService;
    private final TransactionImportRejectedCsvBuilder rejectedCsvBuilder;
    private final TransactionImportStorageService storageService;

    public TransactionImportRejectedOutputPreparationService(TransactionImportRejectedRowStagingService rejectedRowStagingService,
                                                             TransactionImportRejectedCsvBuilder rejectedCsvBuilder,
                                                             TransactionImportStorageService storageService) {
        this.rejectedRowStagingService = rejectedRowStagingService;
        this.rejectedCsvBuilder = rejectedCsvBuilder;
        this.storageService = storageService;
    }

    /*
    Reading all rejected rows from db in a short transaction then closing it. Then building bytes and uploading to s3
     */
    public TransactionImportRejectedOutput prepareAndUpload(Long importId, String sourceObjectKey) {
        Objects.requireNonNull(sourceObjectKey, "Source object key is required");

        List<TransactionImportRejectedRowStaging> rejectedRows = rejectedRowStagingService.findAll(importId);

        if (rejectedRows.isEmpty()) {
            return TransactionImportRejectedOutput.none();
        }

        byte[] rejectedCsv = rejectedCsvBuilder.build(rejectedRows);
        String rejectedObjectKey = storageService.uploadRejectedOutput(sourceObjectKey, rejectedCsv);

        return TransactionImportRejectedOutput.uploaded(rejectedRows.size(), rejectedObjectKey
        );
    }
}