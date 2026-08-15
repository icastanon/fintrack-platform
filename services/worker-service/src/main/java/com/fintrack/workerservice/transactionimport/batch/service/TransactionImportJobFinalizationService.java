package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.idempotency.service.ProcessedMessageService;
import com.fintrack.workerservice.transaction.repository.FinancialTransactionRepository;
import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportRejectedOutput;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import com.fintrack.workerservice.transactionimport.service.TransactionImportProcessingLeaseManager;
import com.fintrack.workerservice.transactionimport.service.TransactionImportService;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
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
    private final TransactionImportProcessingLeaseManager processingLeaseManager;

    public TransactionImportJobFinalizationService(ProcessedMessageService processedMessageService,
                                                   FinancialTransactionRepository financialTransactionRepository,
                                                   TransactionImportService transactionImportService,
                                                   TransactionImportProcessingLeaseManager processingLeaseManager) {
        this.processedMessageService = processedMessageService;
        this.financialTransactionRepository = financialTransactionRepository;
        this.transactionImportService = transactionImportService;
        this.processingLeaseManager = processingLeaseManager;
    }

    @Transactional
    public boolean complete(TransactionImportRequestedEvent event,
                            TransactionImportProcessingAttempt processingAttempt,
                            JobExecution jobExecution,
                            TransactionImportRejectedOutput rejectedOutput) {
        Objects.requireNonNull(event, "Transaction import requested event is required");
        Objects.requireNonNull(processingAttempt, "Transaction import processing attempt is required");
        Objects.requireNonNull(jobExecution, "Job execution is required");
        Objects.requireNonNull(rejectedOutput, "Rejected output is required");

        assertActiveProcessingLease(event, processingAttempt);

        boolean firstCompletion = processedMessageService.recordIfFirst(event.getEventId(),
                CONSUMER_NAME,
                EVENT_TYPE,
                event.getEventVersion());

        if (!firstCompletion) {
            return false;
        }

        long successfulRows = financialTransactionRepository.countByImportId(event.getImportId());
        long batchSkippedRows = calculateSkippedRows(jobExecution);

        if (batchSkippedRows != rejectedOutput.getRejectedRowCount()) {
            throw new IllegalStateException(
                    "Spring Batch skip count does not match durable rejected-row count for import "
                            + event.getImportId() + ": batchSkippedRows=" + batchSkippedRows
                            + ", rejectedRows=" + rejectedOutput.getRejectedRowCount()
            );
        }

        transactionImportService.markCompleted(event.getImportId(),
                event.getAccountId(),
                event.getUserId(),
                successfulRows,
                rejectedOutput.getRejectedRowCount(),
                0,
                rejectedOutput.getObjectKey());

        return true;
    }

    @Transactional
    public void fail(TransactionImportRequestedEvent event,
                     TransactionImportProcessingAttempt processingAttempt,
                     JobExecution jobExecution,
                     String failureSummary) {
        Objects.requireNonNull(event, "Transaction import requested event is required");
        Objects.requireNonNull(processingAttempt, "Transaction import processing attempt is required");
        Objects.requireNonNull(jobExecution, "Job execution is required");

        assertActiveProcessingLease(event, processingAttempt);

        long successfulRows = financialTransactionRepository.countByImportId(event.getImportId());
        long skippedRows = calculateSkippedRows(jobExecution);

        transactionImportService.markFailed(event.getImportId(),
                event.getAccountId(),
                event.getUserId(),
                successfulRows,
                skippedRows,
                0,
                failureSummary);
    }

    private void assertActiveProcessingLease(TransactionImportRequestedEvent event,
                                             TransactionImportProcessingAttempt processingAttempt) {
        processingLeaseManager.assertActive(event.getImportId(),
                event.getAccountId(),
                event.getUserId(),
                processingAttempt.getProcessingOwner(),
                processingAttempt.getFencingToken());
    }

    private long calculateSkippedRows(JobExecution jobExecution) {
        return jobExecution.getStepExecutions()
                .stream()
                .mapToLong(StepExecution::getSkipCount)
                .sum();
    }
}