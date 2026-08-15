package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.idempotency.service.ProcessedMessageService;
import com.fintrack.workerservice.transaction.repository.FinancialTransactionRepository;
import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportRejectedOutput;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportProcessingLeaseLostException;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import com.fintrack.workerservice.transactionimport.service.TransactionImportProcessingLeaseManager;
import com.fintrack.workerservice.transactionimport.service.TransactionImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportJobFinalizationServiceTest {

    private static final UUID EVENT_ID = UUID.fromString("8fb4e595-dbbc-4b7f-a791-8902bf5d93e1");
    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 22L;
    private static final Long USER_ID = 9L;
    private static final String PROCESSING_OWNER = "worker-a";
    private static final long FENCING_TOKEN = 3L;
    private static final String CONSUMER_NAME = "transaction-import-request-processor";
    private static final String EVENT_TYPE = "TRANSACTION_IMPORT_REQUESTED";
    private static final String REJECTED_OBJECT_KEY = "imports/9/import-uuid/rejected.csv";

    private static final TransactionImportProcessingAttempt PROCESSING_ATTEMPT =
            new TransactionImportProcessingAttempt(EVENT_ID,
                    IMPORT_ID,
                    ACCOUNT_ID,
                    USER_ID,
                    PROCESSING_OWNER,
                    FENCING_TOKEN);

    @Mock
    private ProcessedMessageService processedMessageService;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private TransactionImportService transactionImportService;

    @Mock
    private TransactionImportProcessingLeaseManager processingLeaseManager;

    @Mock
    private JobExecution jobExecution;

    @Mock
    private StepExecution firstStepExecution;

    @Mock
    private StepExecution secondStepExecution;

    @InjectMocks
    private TransactionImportJobFinalizationService finalizationService;

    @Test
    void completeValidatesLeaseRecordsMessageCountsRowsAndCompletesImport() {
        TransactionImportRequestedEvent event = event();
        TransactionImportRejectedOutput rejectedOutput =
                TransactionImportRejectedOutput.uploaded(3, REJECTED_OBJECT_KEY);

        when(processedMessageService.recordIfFirst(EVENT_ID,
                CONSUMER_NAME,
                EVENT_TYPE,
                TransactionImportRequestedEvent.CURRENT_VERSION))
                .thenReturn(true);

        when(financialTransactionRepository.countByImportId(IMPORT_ID)).thenReturn(7L);
        when(jobExecution.getStepExecutions()).thenReturn(Set.of(firstStepExecution, secondStepExecution));
        when(firstStepExecution.getSkipCount()).thenReturn(2L);
        when(secondStepExecution.getSkipCount()).thenReturn(1L);

        boolean completed =
                finalizationService.complete(event, PROCESSING_ATTEMPT, jobExecution, rejectedOutput);

        assertThat(completed).isTrue();

        InOrder order = inOrder(processingLeaseManager,
                processedMessageService,
                financialTransactionRepository,
                transactionImportService);

        order.verify(processingLeaseManager).assertActive(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                FENCING_TOKEN);

        order.verify(processedMessageService).recordIfFirst(
                EVENT_ID,
                CONSUMER_NAME,
                EVENT_TYPE,
                TransactionImportRequestedEvent.CURRENT_VERSION
        );

        order.verify(financialTransactionRepository).countByImportId(IMPORT_ID);

        order.verify(transactionImportService).markCompleted(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                7,
                3,
                0,
                REJECTED_OBJECT_KEY);
    }

    @Test
    void completeWithoutSkippedRowsStoresNoRejectedObjectKey() {
        TransactionImportRequestedEvent event = event();
        TransactionImportRejectedOutput rejectedOutput = TransactionImportRejectedOutput.none();

        when(processedMessageService.recordIfFirst(EVENT_ID,
                CONSUMER_NAME,
                EVENT_TYPE,
                TransactionImportRequestedEvent.CURRENT_VERSION))
                .thenReturn(true);

        when(financialTransactionRepository.countByImportId(IMPORT_ID)).thenReturn(7L);
        when(jobExecution.getStepExecutions()).thenReturn(Set.of(firstStepExecution));
        when(firstStepExecution.getSkipCount()).thenReturn(0L);

        boolean completed =
                finalizationService.complete(event, PROCESSING_ATTEMPT, jobExecution, rejectedOutput);

        assertThat(completed).isTrue();

        verify(transactionImportService).markCompleted(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                7,
                0,
                0,
                null);
    }

    @Test
    void completeRejectsMismatchBetweenBatchAndDurableRejectedCounts() {
        TransactionImportRequestedEvent event = event();
        TransactionImportRejectedOutput rejectedOutput =
                TransactionImportRejectedOutput.uploaded(2, REJECTED_OBJECT_KEY);

        when(processedMessageService.recordIfFirst(EVENT_ID,
                CONSUMER_NAME,
                EVENT_TYPE,
                TransactionImportRequestedEvent.CURRENT_VERSION))
                .thenReturn(true);

        when(financialTransactionRepository.countByImportId(IMPORT_ID)).thenReturn(7L);
        when(jobExecution.getStepExecutions()).thenReturn(Set.of(firstStepExecution));
        when(firstStepExecution.getSkipCount()).thenReturn(3L);

        assertThatThrownBy(() ->
                finalizationService.complete(event,
                        PROCESSING_ATTEMPT,
                        jobExecution,
                        rejectedOutput))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Spring Batch skip count does not match durable rejected-row count "
                                + "for import 41: batchSkippedRows=3, rejectedRows=2"
                );

        verifyNoInteractions(transactionImportService);
    }

    @Test
    void completeSkipsDuplicateCompletedMessageAfterValidatingLease() {
        TransactionImportRequestedEvent event = event();
        TransactionImportRejectedOutput rejectedOutput = TransactionImportRejectedOutput.none();

        when(processedMessageService.recordIfFirst(EVENT_ID,
                CONSUMER_NAME,
                EVENT_TYPE,
                TransactionImportRequestedEvent.CURRENT_VERSION))
                .thenReturn(false);

        boolean completed =
                finalizationService.complete(event, PROCESSING_ATTEMPT, jobExecution, rejectedOutput);

        assertThat(completed).isFalse();

        InOrder order = inOrder(processingLeaseManager, processedMessageService);

        order.verify(processingLeaseManager).assertActive(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                FENCING_TOKEN);

        order.verify(processedMessageService).recordIfFirst(
                EVENT_ID,
                CONSUMER_NAME,
                EVENT_TYPE,
                TransactionImportRequestedEvent.CURRENT_VERSION
        );

        verifyNoInteractions(financialTransactionRepository, transactionImportService);
    }

    @Test
    void completeDoesNotChangeStateWhenProcessingLeaseWasLost() {
        TransactionImportRequestedEvent event = event();
        TransactionImportRejectedOutput rejectedOutput = TransactionImportRejectedOutput.none();

        TransactionImportProcessingLeaseLostException leaseLostException =
                new TransactionImportProcessingLeaseLostException(IMPORT_ID,
                        PROCESSING_OWNER,
                        FENCING_TOKEN);

        doThrow(leaseLostException)
                .when(processingLeaseManager)
                .assertActive(IMPORT_ID,
                        ACCOUNT_ID,
                        USER_ID,
                        PROCESSING_OWNER,
                        FENCING_TOKEN);

        assertThatThrownBy(() ->
                finalizationService.complete(event,
                        PROCESSING_ATTEMPT,
                        jobExecution,
                        rejectedOutput))
                .isSameAs(leaseLostException);

        verifyNoInteractions(processedMessageService,
                financialTransactionRepository,
                transactionImportService);
    }

    @Test
    void failValidatesLeaseCountsCommittedAndSkippedRowsWithoutRecordingMessage() {
        TransactionImportRequestedEvent event = event();

        when(financialTransactionRepository.countByImportId(IMPORT_ID)).thenReturn(3L);
        when(jobExecution.getStepExecutions()).thenReturn(Set.of(firstStepExecution));
        when(firstStepExecution.getSkipCount()).thenReturn(2L);

        finalizationService.fail(event,
                PROCESSING_ATTEMPT,
                jobExecution,
                "Database connection failed");

        InOrder order = inOrder(processingLeaseManager,
                financialTransactionRepository,
                transactionImportService);

        order.verify(processingLeaseManager).assertActive(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                FENCING_TOKEN);

        order.verify(financialTransactionRepository).countByImportId(IMPORT_ID);

        order.verify(transactionImportService).markFailed(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                3,
                2,
                0,
                "Database connection failed");

        verifyNoInteractions(processedMessageService);
    }

    @Test
    void failDoesNotChangeStateWhenProcessingLeaseWasLost() {
        TransactionImportRequestedEvent event = event();

        TransactionImportProcessingLeaseLostException leaseLostException =
                new TransactionImportProcessingLeaseLostException(IMPORT_ID,
                        PROCESSING_OWNER,
                        FENCING_TOKEN);

        doThrow(leaseLostException)
                .when(processingLeaseManager)
                .assertActive(IMPORT_ID,
                        ACCOUNT_ID,
                        USER_ID,
                        PROCESSING_OWNER,
                        FENCING_TOKEN);

        assertThatThrownBy(() ->
                finalizationService.fail(event,
                        PROCESSING_ATTEMPT,
                        jobExecution,
                        "Database connection failed"))
                .isSameAs(leaseLostException);

        verifyNoInteractions(processedMessageService,
                financialTransactionRepository,
                transactionImportService);
    }

    @Test
    void completeRejectsNullEventBeforeUsingDependencies() {
        assertThatThrownBy(() ->
                finalizationService.complete(null,
                        PROCESSING_ATTEMPT,
                        jobExecution,
                        TransactionImportRejectedOutput.none()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Transaction import requested event is required");

        verifyNoInteractions(processingLeaseManager,
                processedMessageService,
                financialTransactionRepository,
                transactionImportService);
    }

    @Test
    void completeRejectsNullProcessingAttemptBeforeUsingDependencies() {
        assertThatThrownBy(() ->
                finalizationService.complete(event(),
                        null,
                        jobExecution,
                        TransactionImportRejectedOutput.none()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Transaction import processing attempt is required");

        verifyNoInteractions(processingLeaseManager,
                processedMessageService,
                financialTransactionRepository,
                transactionImportService);
    }

    @Test
    void completeRejectsNullJobExecutionBeforeUsingDependencies() {
        assertThatThrownBy(() ->
                finalizationService.complete(event(),
                        PROCESSING_ATTEMPT,
                        null,
                        TransactionImportRejectedOutput.none()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Job execution is required");

        verifyNoInteractions(processingLeaseManager,
                processedMessageService,
                financialTransactionRepository,
                transactionImportService);
    }

    @Test
    void completeRejectsNullRejectedOutputBeforeUsingDependencies() {
        assertThatThrownBy(() ->
                finalizationService.complete(event(),
                        PROCESSING_ATTEMPT,
                        jobExecution,
                        null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Rejected output is required");

        verifyNoInteractions(processingLeaseManager,
                processedMessageService,
                financialTransactionRepository,
                transactionImportService);
    }

    @Test
    void failRejectsNullEventBeforeUsingDependencies() {
        assertThatThrownBy(() ->
                finalizationService.fail(null,
                        PROCESSING_ATTEMPT,
                        jobExecution,
                        "Failure"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Transaction import requested event is required");

        verifyNoInteractions(processingLeaseManager,
                processedMessageService,
                financialTransactionRepository,
                transactionImportService);
    }

    @Test
    void failRejectsNullProcessingAttemptBeforeUsingDependencies() {
        assertThatThrownBy(() ->
                finalizationService.fail(event(),
                        null,
                        jobExecution,
                        "Failure"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Transaction import processing attempt is required");

        verifyNoInteractions(processingLeaseManager,
                processedMessageService,
                financialTransactionRepository,
                transactionImportService);
    }

    @Test
    void failRejectsNullJobExecutionBeforeUsingDependencies() {
        assertThatThrownBy(() ->
                finalizationService.fail(event(),
                        PROCESSING_ATTEMPT,
                        null,
                        "Failure"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Job execution is required");

        verifyNoInteractions(processingLeaseManager,
                processedMessageService,
                financialTransactionRepository,
                transactionImportService);
    }

    private TransactionImportRequestedEvent event() {
        return TransactionImportRequestedEvent.create(EVENT_ID,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                "imports/9/import-uuid/source.csv",
                "correlation-123",
                Instant.parse("2026-08-12T12:00:00Z"));
    }
}