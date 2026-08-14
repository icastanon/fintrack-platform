package com.fintrack.workerservice.transactionimport.service;

import com.fintrack.workerservice.transactionimport.entity.TransactionImport;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportNotFoundException;
import com.fintrack.workerservice.transactionimport.repository.TransactionImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionImportService {

    private final TransactionImportRepository transactionImportRepository;

    public TransactionImportService(TransactionImportRepository transactionImportRepository) {
        this.transactionImportRepository = transactionImportRepository;
    }

    @Transactional(readOnly = true)
    public TransactionImport getRequestedImport(Long importId, Long accountId, Long userId) {
        return findRequestedImport(importId, accountId, userId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRunning(Long importId, Long accountId, Long userId) {
        TransactionImport transactionImport = findRequestedImport(importId, accountId, userId);
        transactionImport.markRunning();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markCompleted(Long importId, Long accountId, Long userId,
                              long successfulRows, long skippedRows, long failedRows,
                              String rejectedObjectKey) {
        TransactionImport transactionImport = findRequestedImport(importId, accountId, userId);
        transactionImport.markCompleted(successfulRows, skippedRows, failedRows, rejectedObjectKey);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markFailed(Long importId, Long accountId, Long userId,
                           long successfulRows, long skippedRows, long failedRows,
                           String failureSummary) {
        TransactionImport transactionImport = findRequestedImport(importId, accountId, userId);
        transactionImport.markFailed(
                successfulRows,
                skippedRows,
                failedRows,
                failureSummary
        );
    }

    private TransactionImport findRequestedImport(Long importId, Long accountId, Long userId) {
        return transactionImportRepository
                .findByIdAndAccountIdAndUserId(importId, accountId, userId)
                .orElseThrow(() -> new TransactionImportNotFoundException(importId, accountId, userId));
    }
}