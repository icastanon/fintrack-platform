package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.workerservice.transactionimport.entity.TransactionImportRejectedRowStaging;
import com.fintrack.workerservice.transactionimport.repository.TransactionImportRejectedRowStagingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class TransactionImportRejectedRowStagingService {

    private final TransactionImportRejectedRowStagingRepository rejectedRowStagingRepository;

    public TransactionImportRejectedRowStagingService(TransactionImportRejectedRowStagingRepository rejectedRowStagingRepository) {
        this.rejectedRowStagingRepository = rejectedRowStagingRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    //creating entity to validate values
    public boolean stage(Long importId, int rowNumber, String rawRecord, String failureReason) {
        TransactionImportRejectedRowStaging rejectedRow =
                TransactionImportRejectedRowStaging.create(importId, rowNumber, rawRecord, failureReason);

        int insertedRows = rejectedRowStagingRepository.insertIfAbsent(
                rejectedRow.getImportId(),
                rejectedRow.getRowNumber(),
                rejectedRow.getRawRecord(),
                rejectedRow.getFailureReason()
        );

        return insertedRows == 1;
    }

    @Transactional(readOnly = true)
    public List<TransactionImportRejectedRowStaging> findAll(Long importId) {
        requirePositiveImportId(importId);
        return rejectedRowStagingRepository.findAllByImportIdOrderByRowNumberAsc(importId);
    }

    @Transactional(readOnly = true)
    public long count(Long importId) {
        requirePositiveImportId(importId);
        return rejectedRowStagingRepository.countByImportId(importId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteAll(Long importId) {
        requirePositiveImportId(importId);
        return rejectedRowStagingRepository.deleteAllByImportId(importId);
    }

    @Transactional
    public int deleteAllForCompletedImportsBefore(Instant completedBefore) {
        Objects.requireNonNull(completedBefore, "Completed-before cutoff is required");
        return rejectedRowStagingRepository.deleteAllForCompletedImportsBefore(completedBefore);
    }

    private void requirePositiveImportId(Long importId) {
        if (importId == null || importId <= 0) {
            throw new IllegalArgumentException("Import ID must be positive");
        }
    }
}