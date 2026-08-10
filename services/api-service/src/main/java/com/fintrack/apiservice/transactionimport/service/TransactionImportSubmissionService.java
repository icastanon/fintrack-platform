package com.fintrack.apiservice.transactionimport.service;

import com.fintrack.apiservice.account.entity.AccountStatus;
import com.fintrack.apiservice.account.entity.FinancialAccount;
import com.fintrack.apiservice.account.exception.FinancialAccountClosedException;
import com.fintrack.apiservice.account.exception.FinancialAccountNotFoundException;
import com.fintrack.apiservice.account.repository.FinancialAccountRepository;
import com.fintrack.apiservice.transactionimport.dto.TransactionImportResponse;
import com.fintrack.apiservice.transactionimport.entity.TransactionImport;
import com.fintrack.apiservice.transactionimport.exception.TransactionImportStorageException;
import com.fintrack.apiservice.transactionimport.mapper.TransactionImportMapper;
import com.fintrack.apiservice.transactionimport.storage.TransactionImportStorageService;
import com.fintrack.apiservice.transactionimport.validation.TransactionImportFileValidator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class TransactionImportSubmissionService {

    private final FinancialAccountRepository financialAccountRepository;
    private final TransactionImportFileValidator fileValidator;
    private final TransactionImportStorageService storageService;
    private final TransactionImportPersistenceService persistenceService;
    private final TransactionImportMapper transactionImportMapper;

    public TransactionImportSubmissionService(FinancialAccountRepository financialAccountRepository,
                                              TransactionImportFileValidator fileValidator,
                                              TransactionImportStorageService storageService,
                                              TransactionImportPersistenceService persistenceService,
                                              TransactionImportMapper transactionImportMapper) {
        this.financialAccountRepository = financialAccountRepository;
        this.fileValidator = fileValidator;
        this.storageService = storageService;
        this.persistenceService = persistenceService;
        this.transactionImportMapper = transactionImportMapper;
    }

    public TransactionImportResponse submit(Long userId, Long accountId, MultipartFile file) {
        String originalFileName = fileValidator.validate(file);

        verifyOwnedActiveAccount(userId, accountId);

        String contentType = file.getContentType();
        String sourceObjectKey = uploadFile(userId, file, contentType);

        TransactionImport transactionImport;

        try {
            //transactional method that saves into import table and outbox event atomically
            transactionImport = persistenceService.createQueuedImport(
                    userId,
                    accountId,
                    originalFileName,
                    contentType,
                    file.getSize(),
                    sourceObjectKey
            );
        } catch (RuntimeException exception) {
            storageService.deleteQuietly(sourceObjectKey);
            throw exception;
        }

        return transactionImportMapper.toResponse(transactionImport);
    }

    private void verifyOwnedActiveAccount(Long userId, Long accountId) {
        FinancialAccount account = financialAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(FinancialAccountNotFoundException::new);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new FinancialAccountClosedException();
        }
    }

    private String uploadFile(Long userId, MultipartFile file, String contentType) {
        try (InputStream inputStream = file.getInputStream()) {
            return storageService.upload(userId, inputStream, file.getSize(), contentType);
        } catch (IOException exception) {
            throw new TransactionImportStorageException("Failed to read the transaction import file", exception);
        }
    }
}