package com.fintrack.workerservice.transactionimport.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportJobFinalizationService;
import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportJobLaunchService;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportJobProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobRestartException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportRequestedEventProcessorTest {

    private static final UUID EVENT_ID = UUID.fromString("8fb4e595-dbbc-4b7f-a791-8902bf5d93e1");
    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 22L;
    private static final Long USER_ID = 9L;
    private static final Long JOB_EXECUTION_ID = 81L;

    @Mock
    private TransactionImportJobLaunchService jobLaunchService;

    @Mock
    private TransactionImportJobFinalizationService jobFinalizationService;

    @Mock
    private JobExecution jobExecution;

    @InjectMocks
    private TransactionImportRequestedEventProcessor eventProcessor;

    @Test
    void processCompletesSuccessfulExecution() throws Exception {
        TransactionImportRequestedEvent event = event();

        when(jobLaunchService.launch(event)).thenReturn(jobExecution);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getId()).thenReturn(JOB_EXECUTION_ID);
        when(jobFinalizationService.complete(event, jobExecution)).thenReturn(true);

        boolean firstCompletion = eventProcessor.process(event);

        assertThat(firstCompletion).isTrue();

        verify(jobLaunchService).launch(event);
        verify(jobFinalizationService).complete(event, jobExecution);
    }

    @Test
    void processReturnsFalseWhenCompletionWasAlreadyFinalized() throws Exception {
        TransactionImportRequestedEvent event = event();

        when(jobLaunchService.launch(event)).thenReturn(jobExecution);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getId()).thenReturn(JOB_EXECUTION_ID);
        when(jobFinalizationService.complete(event, jobExecution)).thenReturn(false);

        boolean firstCompletion = eventProcessor.process(event);

        assertThat(firstCompletion).isFalse();

        verify(jobLaunchService).launch(event);
        verify(jobFinalizationService).complete(event, jobExecution);
    }

    @Test
    void processFinalizesPersistedCompletedExecution() throws Exception {
        TransactionImportRequestedEvent event = event();
        JobInstanceAlreadyCompleteException cause =
                new JobInstanceAlreadyCompleteException("Job instance already completed");

        when(jobLaunchService.launch(event)).thenThrow(cause);
        when(jobLaunchService.findLastExecution(event)).thenReturn(Optional.of(jobExecution));
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getId()).thenReturn(JOB_EXECUTION_ID);
        when(jobFinalizationService.complete(event, jobExecution)).thenReturn(true);

        boolean firstCompletion = eventProcessor.process(event);

        assertThat(firstCompletion).isTrue();

        verify(jobLaunchService).launch(event);
        verify(jobLaunchService).findLastExecution(event);
        verify(jobFinalizationService).complete(event, jobExecution);
    }

    @Test
    void processDoesNotTreatPersistedAbandonedExecutionAsCompleted() throws Exception {
        TransactionImportRequestedEvent event = event();
        JobInstanceAlreadyCompleteException cause =
                new JobInstanceAlreadyCompleteException("Job instance cannot be restarted");
        String expectedSummary =
                "Transaction import job execution 81 finished with status ABANDONED";

        when(jobLaunchService.launch(event)).thenThrow(cause);
        when(jobLaunchService.findLastExecution(event)).thenReturn(Optional.of(jobExecution));
        when(jobExecution.getStatus()).thenReturn(BatchStatus.ABANDONED);
        when(jobExecution.getId()).thenReturn(JOB_EXECUTION_ID);
        when(jobExecution.getAllFailureExceptions()).thenReturn(List.of());

        assertThatThrownBy(() -> eventProcessor.process(event))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage(expectedSummary);

        verify(jobLaunchService).findLastExecution(event);
        verify(jobFinalizationService).fail(
                event,
                jobExecution,
                expectedSummary
        );
    }

    @Test
    void processRejectsExistingTerminalInstanceWithoutExecutionMetadata() throws Exception {
        TransactionImportRequestedEvent event = event();
        JobInstanceAlreadyCompleteException cause =
                new JobInstanceAlreadyCompleteException("Job instance already completed");

        when(jobLaunchService.launch(event)).thenThrow(cause);
        when(jobLaunchService.findLastExecution(event)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventProcessor.process(event))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage(
                        "Spring Batch reported an existing terminal job instance but no execution metadata was found for import 41"
                )
                .hasCause(cause);

        verify(jobLaunchService).findLastExecution(event);
        verifyNoInteractions(jobFinalizationService);
    }

    @Test
    void processRejectsAlreadyRunningJobExecution() throws Exception {
        TransactionImportRequestedEvent event = event();
        JobExecutionAlreadyRunningException cause =
                new JobExecutionAlreadyRunningException("Job execution is already running");

        when(jobLaunchService.launch(event)).thenThrow(cause);

        assertThatThrownBy(() -> eventProcessor.process(event))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage("Transaction import job is already running for import 41")
                .hasCause(cause);

        verifyNoInteractions(jobFinalizationService);
    }

    @Test
    void processWrapsOtherJobLaunchFailures() throws Exception {
        TransactionImportRequestedEvent event = event();
        JobRestartException cause = new JobRestartException("Job could not be restarted");

        when(jobLaunchService.launch(event)).thenThrow(cause);

        assertThatThrownBy(() -> eventProcessor.process(event))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage("Failed to launch transaction import job for import 41")
                .hasCause(cause);

        verifyNoInteractions(jobFinalizationService);
    }

    @Test
    void processMarksFailedExecutionAndPropagatesFailure() throws Exception {
        TransactionImportRequestedEvent event = event();
        String expectedSummary =
                "Transaction import job execution 81 finished with status FAILED: Database unavailable";

        when(jobLaunchService.launch(event)).thenReturn(jobExecution);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobExecution.getId()).thenReturn(JOB_EXECUTION_ID);
        when(jobExecution.getAllFailureExceptions())
                .thenReturn(List.of(new IllegalStateException("Database unavailable")));

        assertThatThrownBy(() -> eventProcessor.process(event))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage(expectedSummary);

        verify(jobFinalizationService).fail(
                event,
                jobExecution,
                expectedSummary
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = BatchStatus.class,
            names = {"STOPPED", "ABANDONED", "UNKNOWN"}
    )
    void processMarksOtherUnsuccessfulTerminalStatusesAsFailed(BatchStatus status) throws Exception {
        TransactionImportRequestedEvent event = event();
        String expectedSummary =
                "Transaction import job execution 81 finished with status " + status;

        when(jobLaunchService.launch(event)).thenReturn(jobExecution);
        when(jobExecution.getStatus()).thenReturn(status);
        when(jobExecution.getId()).thenReturn(JOB_EXECUTION_ID);
        when(jobExecution.getAllFailureExceptions()).thenReturn(List.of());

        assertThatThrownBy(() -> eventProcessor.process(event))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage(expectedSummary);

        verify(jobFinalizationService).fail(
                event,
                jobExecution,
                expectedSummary
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = BatchStatus.class,
            names = {"STARTING", "STARTED", "STOPPING"}
    )
    void processRejectsNonTerminalExecutionWithoutMarkingImportFailed(BatchStatus status)
            throws Exception {
        TransactionImportRequestedEvent event = event();

        when(jobLaunchService.launch(event)).thenReturn(jobExecution);
        when(jobExecution.getStatus()).thenReturn(status);

        assertThatThrownBy(() -> eventProcessor.process(event))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage(
                        "Transaction import job returned before reaching a terminal status for import 41: "
                                + status
                );

        verifyNoInteractions(jobFinalizationService);
    }

    @Test
    void processRejectsNullEventBeforeUsingDependencies() {
        assertThatThrownBy(() -> eventProcessor.process(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Transaction import requested event is required");

        verifyNoInteractions(jobLaunchService, jobFinalizationService);
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