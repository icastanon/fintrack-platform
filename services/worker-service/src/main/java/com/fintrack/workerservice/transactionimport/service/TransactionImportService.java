package com.fintrack.workerservice.transactionimport.service;

import com.fintrack.workerservice.transactionimport.entity.TransactionImport;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportNotFoundException;
import com.fintrack.workerservice.transactionimport.repository.TransactionImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionImportService {

    private final TransactionImportRepository transactionImportRepository;

    public TransactionImportService(TransactionImportRepository transactionImportRepository) {
        this.transactionImportRepository = transactionImportRepository;
    }

    @Transactional(readOnly = true)
    public TransactionImport getRequestedImport(Long importId, Long accountId, Long userId) {
        return transactionImportRepository
                .findByIdAndAccountIdAndUserId(importId, accountId, userId)
                .orElseThrow(() -> new TransactionImportNotFoundException(importId, accountId, userId));
    }
}