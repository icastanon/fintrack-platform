package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.workerservice.transactionimport.entity.TransactionImportRejectedRowStaging;
import com.fintrack.workerservice.transactionimport.repository.TransactionImportRejectedRowStagingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionImportRejectedRowStagingService {

    private final TransactionImportRejectedRowStagingRepository rejectedRowStagingRepository;

    public TransactionImportRejectedRowStagingService(TransactionImportRejectedRowStagingRepository rejectedRowStagingRepository) {
        this.rejectedRowStagingRepository = rejectedRowStagingRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean stage(Long importId, int rowNumber, String rawRecord, String failureReason) {
        //creating entity to validate values
        TransactionImportRejectedRowStaging rejectedRow = TransactionImportRejectedRowStaging.create(
                        importId,
                        rowNumber,
                        rawRecord,
                        failureReason
                );

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

    @Transactional(propagation = Propagation.MANDATORY)
    public int deleteAll(Long importId) {
        requirePositiveImportId(importId);
        return rejectedRowStagingRepository.deleteAllByImportId(importId);
    }

    private void requirePositiveImportId(Long importId) {
        if (importId == null || importId <= 0) {
            throw new IllegalArgumentException("Import ID must be positive");
        }
    }
}