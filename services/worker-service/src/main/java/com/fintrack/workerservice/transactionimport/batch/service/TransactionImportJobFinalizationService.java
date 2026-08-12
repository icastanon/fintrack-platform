package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.idempotency.service.ProcessedMessageService;
import com.fintrack.workerservice.transaction.repository.FinancialTransactionRepository;
import com.fintrack.workerservice.transactionimport.service.TransactionImportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class TransactionImportJobFinalizationService {

    private static final String CONSUMER_NAME = "transaction-import-request-processor";
    private static final String EVENT_TYPE = "TRANSACTION_IMPORT_REQUESTED";

    private final ProcessedMessageService processedMessageService;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final TransactionImportService transactionImportService;

    public TransactionImportJobFinalizationService(ProcessedMessageService processedMessageService,
                                                   FinancialTransactionRepository financialTransactionRepository,
                                                   TransactionImportService transactionImportService) {
        this.processedMessageService = processedMessageService;
        this.financialTransactionRepository = financialTransactionRepository;
        this.transactionImportService = transactionImportService;
    }

    @Transactional
    public boolean complete(TransactionImportRequestedEvent event) {
        Objects.requireNonNull(event, "Transaction import requested event is required");

        boolean firstCompletion = processedMessageService.recordIfFirst(
                event.getEventId(),
                CONSUMER_NAME,
                EVENT_TYPE,
                event.getEventVersion()
        );

        if (!firstCompletion) {
            return false;
        }

        long successfulRows = financialTransactionRepository.countByImportId(event.getImportId());

        transactionImportService.markCompleted(
                event.getImportId(),
                event.getAccountId(),
                event.getUserId(),
                successfulRows,
                0,
                0
        );

        return true;
    }

    @Transactional
    public void fail(TransactionImportRequestedEvent event, String failureSummary) {
        Objects.requireNonNull(event, "Transaction import requested event is required");

        long successfulRows = financialTransactionRepository.countByImportId(event.getImportId());

        transactionImportService.markFailed(
                event.getImportId(),
                event.getAccountId(),
                event.getUserId(),
                successfulRows,
                0,
                0,
                failureSummary
        );
    }
}