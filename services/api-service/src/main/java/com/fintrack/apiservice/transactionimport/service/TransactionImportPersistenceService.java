package com.fintrack.apiservice.transactionimport.service;

import com.fintrack.apiservice.account.entity.AccountStatus;
import com.fintrack.apiservice.account.entity.FinancialAccount;
import com.fintrack.apiservice.account.exception.FinancialAccountClosedException;
import com.fintrack.apiservice.account.exception.FinancialAccountNotFoundException;
import com.fintrack.apiservice.account.repository.FinancialAccountRepository;
import com.fintrack.apiservice.outbox.service.OutboxEventWriter;
import com.fintrack.apiservice.transactionimport.entity.TransactionImport;
import com.fintrack.apiservice.transactionimport.repository.TransactionImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionImportPersistenceService {

    private final FinancialAccountRepository financialAccountRepository;
    private final TransactionImportRepository transactionImportRepository;
    private final OutboxEventWriter outboxEventWriter;

    public TransactionImportPersistenceService(FinancialAccountRepository financialAccountRepository,
                                               TransactionImportRepository transactionImportRepository,
                                               OutboxEventWriter outboxEventWriter) {
        this.financialAccountRepository = financialAccountRepository;
        this.transactionImportRepository = transactionImportRepository;
        this.outboxEventWriter = outboxEventWriter;
    }

    //Writes into outbox and import tables atomically. One row on each.
    @Transactional
    public TransactionImport createQueuedImport(Long userId,
                                                Long accountId,
                                                String originalFileName,
                                                String contentType,
                                                long fileSizeBytes,
                                                String sourceObjectKey) {
        FinancialAccount account = financialAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(FinancialAccountNotFoundException::new);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new FinancialAccountClosedException();
        }

        TransactionImport transactionImport = TransactionImport.createQueued(
                account,
                originalFileName,
                contentType,
                fileSizeBytes,
                sourceObjectKey
        );

        TransactionImport savedImport = transactionImportRepository.saveAndFlush(transactionImport);

        outboxEventWriter.writeTransactionImportRequested(
                savedImport.getId(),
                accountId,
                userId,
                sourceObjectKey
        );

        return savedImport;
    }
}