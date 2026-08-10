package com.fintrack.apiservice.transactionimport.service;

import com.fintrack.apiservice.transactionimport.dto.TransactionImportResponse;
import com.fintrack.apiservice.transactionimport.entity.TransactionImport;
import com.fintrack.apiservice.transactionimport.exception.TransactionImportNotFoundException;
import com.fintrack.apiservice.transactionimport.mapper.TransactionImportMapper;
import com.fintrack.apiservice.transactionimport.repository.TransactionImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TransactionImportService {

    private final TransactionImportRepository transactionImportRepository;
    private final TransactionImportMapper transactionImportMapper;

    public TransactionImportService(TransactionImportRepository transactionImportRepository,
                                    TransactionImportMapper transactionImportMapper) {
        this.transactionImportRepository = transactionImportRepository;
        this.transactionImportMapper = transactionImportMapper;
    }

    public TransactionImportResponse getImport(Long userId, Long importId) {
        TransactionImport transactionImport = transactionImportRepository
                .findByIdAndAccountUserId(importId, userId)
                .orElseThrow(TransactionImportNotFoundException::new);

        return transactionImportMapper.toResponse(transactionImport);
    }
}