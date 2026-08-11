package com.fintrack.apiservice.transactionimport.service;

import com.fintrack.apiservice.transactionimport.dto.TransactionImportRejectedOutput;
import com.fintrack.apiservice.transactionimport.entity.TransactionImport;
import com.fintrack.apiservice.transactionimport.exception.TransactionImportNotFoundException;
import com.fintrack.apiservice.transactionimport.exception.TransactionImportRejectedOutputNotAvailableException;
import com.fintrack.apiservice.transactionimport.repository.TransactionImportRepository;
import com.fintrack.apiservice.transactionimport.storage.TransactionImportStorageService;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class TransactionImportRejectedOutputService {

    private final TransactionImportRepository transactionImportRepository;
    private final TransactionImportStorageService storageService;

    public TransactionImportRejectedOutputService(TransactionImportRepository transactionImportRepository,
                                                  TransactionImportStorageService storageService) {
        this.transactionImportRepository = transactionImportRepository;
        this.storageService = storageService;
    }

    public TransactionImportRejectedOutput getRejectedOutput(Long userId, Long importId) {
        TransactionImport transactionImport = transactionImportRepository
                .findByIdAndAccountUserId(importId, userId)
                .orElseThrow(TransactionImportNotFoundException::new);

        String rejectedObjectKey = transactionImport.getRejectedObjectKey();

        if (rejectedObjectKey == null || rejectedObjectKey.isBlank()) {
            throw new TransactionImportRejectedOutputNotAvailableException();
        }

        byte[] content = storageService.download(rejectedObjectKey);
        String fileName = buildRejectedFileName(transactionImport.getOriginalFileName());

        return new TransactionImportRejectedOutput(fileName, content);
    }

    private String buildRejectedFileName(String originalFileName) {
        String lowercaseFileName = originalFileName.toLowerCase(Locale.ROOT);
        int extensionIndex = lowercaseFileName.lastIndexOf(".csv");

        String baseFileName = extensionIndex >= 0
                ? originalFileName.substring(0, extensionIndex)
                : originalFileName;

        return baseFileName + "-rejected.csv";
    }
}