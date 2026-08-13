package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.idempotency.service.ProcessedMessageService;
import com.fintrack.workerservice.transaction.repository.FinancialTransactionRepository;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportJobFinalizationServiceTest {

    private static final UUID EVENT_ID =
            UUID.fromString("8fb4e595-dbbc-4b7f-a791-8902bf5d93e1");

    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 22L;
    private static final Long USER_ID = 9L;

    private static final String CONSUMER_NAME = "transaction-import-request-processor";
    private static final String EVENT_TYPE = "TRANSACTION_IMPORT_REQUESTED";

    @Mock
    private ProcessedMessageService processedMessageService;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private TransactionImportService transactionImportService;

    @Mock
    private JobExecution jobExecution;

    @Mock
    private StepExecution firstStepExecution;

    @Mock
    private StepExecution secondStepExecution;

    @InjectMocks
    private TransactionImportJobFinalizationService finalizationService;

    @Test
    void completeRecordsMessageCountsDurableRowsAndCompletesImport() {
        TransactionImportRequestedEvent event = event();

        when(processedMessageService.recordIfFirst(
                EVENT_ID,
                CONSUMER_NAME,
                EVENT_TYPE,
                TransactionImportRequestedEvent.CURRENT_VERSION
        )).thenReturn(true);

        when(financialTransactionRepository.countByImportId(IMPORT_ID)).thenReturn(7L);
        when(jobExecution.getStepExecutions())
                .thenReturn(Set.of(firstStepExecution, secondStepExecution));
        when(firstStepExecution.getSkipCount()).thenReturn(2L);
        when(secondStepExecution.getSkipCount()).thenReturn(1L);

        boolean completed = finalizationService.complete(event, jobExecution);

        assertThat(completed).isTrue();

        InOrder order = inOrder(
                processedMessageService,
                financialTransactionRepository,
                transactionImportService
        );

        order.verify(processedMessageService).recordIfFirst(
                EVENT_ID,
                CONSUMER_NAME,
                EVENT_TYPE,
                TransactionImportRequestedEvent.CURRENT_VERSION
        );
        order.verify(financialTransactionRepository).countByImportId(IMPORT_ID);
        order.verify(transactionImportService).markCompleted(
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                7,
                3,
                0
        );
    }

    @Test
    void completeSkipsDuplicateCompletedMessage() {
        TransactionImportRequestedEvent event = event();

        when(processedMessageService.recordIfFirst(
                EVENT_ID,
                CONSUMER_NAME,
                EVENT_TYPE,
                TransactionImportRequestedEvent.CURRENT_VERSION
        )).thenReturn(false);

        boolean completed = finalizationService.complete(event, jobExecution);

        assertThat(completed).isFalse();

        verify(processedMessageService).recordIfFirst(
                EVENT_ID,
                CONSUMER_NAME,
                EVENT_TYPE,
                TransactionImportRequestedEvent.CURRENT_VERSION
        );
        verifyNoInteractions(
                financialTransactionRepository,
                transactionImportService
        );
    }

    @Test
    void failCountsCommittedAndSkippedRowsWithoutRecordingMessage() {
        TransactionImportRequestedEvent event = event();

        when(financialTransactionRepository.countByImportId(IMPORT_ID)).thenReturn(3L);
        when(jobExecution.getStepExecutions()).thenReturn(Set.of(firstStepExecution));
        when(firstStepExecution.getSkipCount()).thenReturn(2L);

        finalizationService.fail(
                event,
                jobExecution,
                "Database connection failed"
        );

        InOrder order = inOrder(
                financialTransactionRepository,
                transactionImportService
        );

        order.verify(financialTransactionRepository).countByImportId(IMPORT_ID);
        order.verify(transactionImportService).markFailed(
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                3,
                2,
                0,
                "Database connection failed"
        );

        verifyNoInteractions(processedMessageService);
    }

    @Test
    void completeRejectsNullEventBeforeUsingDependencies() {
        assertThatThrownBy(() -> finalizationService.complete(null, jobExecution))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Transaction import requested event is required");

        verifyNoInteractions(
                processedMessageService,
                financialTransactionRepository,
                transactionImportService
        );
    }

    @Test
    void completeRejectsNullJobExecutionBeforeUsingDependencies() {
        assertThatThrownBy(() -> finalizationService.complete(event(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Job execution is required");

        verifyNoInteractions(
                processedMessageService,
                financialTransactionRepository,
                transactionImportService
        );
    }

    @Test
    void failRejectsNullEventBeforeUsingDependencies() {
        assertThatThrownBy(() ->
                finalizationService.fail(
                        null,
                        jobExecution,
                        "Failure"
                ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Transaction import requested event is required");

        verifyNoInteractions(
                processedMessageService,
                financialTransactionRepository,
                transactionImportService
        );
    }

    @Test
    void failRejectsNullJobExecutionBeforeUsingDependencies() {
        assertThatThrownBy(() ->
                finalizationService.fail(
                        event(),
                        null,
                        "Failure"
                ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Job execution is required");

        verifyNoInteractions(
                processedMessageService,
                financialTransactionRepository,
                transactionImportService
        );
    }

    private TransactionImportRequestedEvent event() {
        return TransactionImportRequestedEvent.create(
                EVENT_ID,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                "imports/9/import-uuid/source.csv",
                "correlation-123",
                Instant.parse("2026-08-12T12:00:00Z")
        );
    }
}