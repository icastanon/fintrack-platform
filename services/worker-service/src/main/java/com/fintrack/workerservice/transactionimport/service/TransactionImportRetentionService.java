package com.fintrack.workerservice.transactionimport.service;

import com.fintrack.workerservice.transactionimport.entity.TransactionImport;
import com.fintrack.workerservice.transactionimport.model.TransactionImportAbandonmentResult;
import com.fintrack.workerservice.transactionimport.repository.TransactionImportRejectedRowStagingRepository;
import com.fintrack.workerservice.transactionimport.repository.TransactionImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class TransactionImportRetentionService {

    private final TransactionImportRepository transactionImportRepository;
    private final TransactionImportRejectedRowStagingRepository rejectedRowStagingRepository;

    public TransactionImportRetentionService(TransactionImportRepository transactionImportRepository,
                                             TransactionImportRejectedRowStagingRepository rejectedRowStagingRepository) {
        this.transactionImportRepository = transactionImportRepository;
        this.rejectedRowStagingRepository = rejectedRowStagingRepository;
    }

    @Transactional
    public TransactionImportAbandonmentResult abandonStaleFailedImports(Instant failedBefore, int batchSize) {
        Objects.requireNonNull(failedBefore, "Failed-before cutoff is required");

        if (batchSize <= 0) {
            throw new IllegalArgumentException("Abandonment batch size must be positive");
        }

        List<TransactionImport> staleFailedImports = transactionImportRepository.findStaleFailedImportsForUpdate(failedBefore, batchSize);

        if (staleFailedImports.isEmpty()) {
            return new TransactionImportAbandonmentResult(0, 0);
        }

        staleFailedImports.forEach(TransactionImport::markAbandoned);
        transactionImportRepository.flush();

        List<Long> importIds = staleFailedImports.stream()
                .map(TransactionImport::getId)
                .toList();

        int deletedRejectedRows = rejectedRowStagingRepository.deleteAllByImportIds(importIds);

        return new TransactionImportAbandonmentResult(staleFailedImports.size(), deletedRejectedRows);
    }
}