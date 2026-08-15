package com.fintrack.workerservice.transactionimport.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportRejectedOutput;
import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportJobFinalizationService;
import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportJobLaunchService;
import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportRejectedOutputPreparationService;
import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportRejectedRowStagingService;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportJobProcessingException;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
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
    private static final String SOURCE_OBJECT_KEY = "imports/9/import-uuid/source.csv";
    private static final String REJECTED_OBJECT_KEY = "imports/9/import-uuid/rejected.csv";

    private static final TransactionImportProcessingAttempt PROCESSING_ATTEMPT =
            new TransactionImportProcessingAttempt(EVENT_ID,
                    IMPORT_ID,
                    ACCOUNT_ID,
                    USER_ID,
                    "worker-a",
                    3L);

    @Mock
    private TransactionImportJobLaunchService jobLaunchService;

    @Mock
    private TransactionImportRejectedOutputPreparationService rejectedOutputPreparationService;

    @Mock
    private TransactionImportJobFinalizationService jobFinalizationService;

    @Mock
    private TransactionImportRejectedRowStagingService rejectedRowStagingService;

    @Mock
    private JobExecution jobExecution;

    @InjectMocks
    private TransactionImportRequestedEventProcessor eventProcessor;

    @Test
    void processCompletesSuccessfulExecutionThenDeletesRejectedRowStaging() throws Exception {
        TransactionImportRequestedEvent event = event();
        TransactionImportRejectedOutput rejectedOutput =
                TransactionImportRejectedOutput.uploaded(2, REJECTED_OBJECT_KEY);

        successfulExecution(event, rejectedOutput);
        when(rejectedRowStagingService.deleteAll(IMPORT_ID)).thenReturn(2);

        boolean firstCompletion = eventProcessor.process(event, PROCESSING_ATTEMPT);

        assertThat(firstCompletion).isTrue();

        InOrder order = inOrder(rejectedOutputPreparationService,
                jobFinalizationService,
                rejectedRowStagingService);

        order.verify(rejectedOutputPreparationService).prepareAndUpload(IMPORT_ID, SOURCE_OBJECT_KEY);
        order.verify(jobFinalizationService).complete(event,
                PROCESSING_ATTEMPT,
                jobExecution,
                rejectedOutput);
        order.verify(rejectedRowStagingService).deleteAll(IMPORT_ID);
    }

    @Test
    void processRecoversRunningExecutionBeforeLaunchingReplacement() throws Exception {
        TransactionImportRequestedEvent event = event();
        TransactionImportRejectedOutput rejectedOutput = TransactionImportRejectedOutput.none();

        when(jobLaunchService.recoverLastExecutionIfRunning(event)).thenReturn(true);
        successfulExecution(event, rejectedOutput);
        when(rejectedRowStagingService.deleteAll(IMPORT_ID)).thenReturn(0);

        boolean firstCompletion = eventProcessor.process(event, PROCESSING_ATTEMPT);

        assertThat(firstCompletion).isTrue();

        InOrder order = inOrder(jobLaunchService);

        order.verify(jobLaunchService).recoverLastExecutionIfRunning(event);
        order.verify(jobLaunchService).launch(event, PROCESSING_ATTEMPT);
    }

    @Test
    void processCompletesExecutionWithoutRejectedRowsAndRunsIdempotentCleanup() throws Exception {
        TransactionImportRequestedEvent event = event();
        TransactionImportRejectedOutput rejectedOutput = TransactionImportRejectedOutput.none();

        successfulExecution(event, rejectedOutput);
        when(rejectedRowStagingService.deleteAll(IMPORT_ID)).thenReturn(0);

        boolean firstCompletion = eventProcessor.process(event, PROCESSING_ATTEMPT);

        assertThat(firstCompletion).isTrue();

        verify(rejectedOutputPreparationService).prepareAndUpload(IMPORT_ID, SOURCE_OBJECT_KEY);
        verify(jobFinalizationService).complete(event,
                PROCESSING_ATTEMPT,
                jobExecution,
                rejectedOutput);
        verify(rejectedRowStagingService).deleteAll(IMPORT_ID);
    }

    @Test
    void processReturnsFalseAndRetriesCleanupWhenCompletionWasAlreadyFinalized() throws Exception {
        TransactionImportRequestedEvent event = event();
        TransactionImportRejectedOutput rejectedOutput = TransactionImportRejectedOutput.none();

        when(jobLaunchService.launch(event, PROCESSING_ATTEMPT)).thenReturn(jobExecution);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getId()).thenReturn(JOB_EXECUTION_ID);
        when(rejectedOutputPreparationService.prepareAndUpload(IMPORT_ID, SOURCE_OBJECT_KEY))
                .thenReturn(rejectedOutput);
        when(jobFinalizationService.complete(event,
                PROCESSING_ATTEMPT,
                jobExecution,
                rejectedOutput))
                .thenReturn(false);
        when(rejectedRowStagingService.deleteAll(IMPORT_ID)).thenReturn(2);

        boolean firstCompletion = eventProcessor.process(event, PROCESSING_ATTEMPT);

        assertThat(firstCompletion).isFalse();

        verify(jobFinalizationService).complete(event,
                PROCESSING_ATTEMPT,
                jobExecution,
                rejectedOutput);
        verify(rejectedRowStagingService).deleteAll(IMPORT_ID);
    }

    @Test
    void processFinalizesPersistedCompletedExecutionThenRunsCleanup() throws Exception {
        TransactionImportRequestedEvent event = event();
        TransactionImportRejectedOutput rejectedOutput =
                TransactionImportRejectedOutput.uploaded(2, REJECTED_OBJECT_KEY);

        JobInstanceAlreadyCompleteException cause =
                new JobInstanceAlreadyCompleteException("Job instance already completed");

        when(jobLaunchService.launch(event, PROCESSING_ATTEMPT)).thenThrow(cause);
        when(jobLaunchService.findLastExecution(event)).thenReturn(Optional.of(jobExecution));
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getId()).thenReturn(JOB_EXECUTION_ID);
        when(rejectedOutputPreparationService.prepareAndUpload(IMPORT_ID, SOURCE_OBJECT_KEY))
                .thenReturn(rejectedOutput);
        when(jobFinalizationService.complete(event,
                PROCESSING_ATTEMPT,
                jobExecution,
                rejectedOutput))
                .thenReturn(true);
        when(rejectedRowStagingService.deleteAll(IMPORT_ID)).thenReturn(2);

        boolean firstCompletion = eventProcessor.process(event, PROCESSING_ATTEMPT);

        assertThat(firstCompletion).isTrue();

        verify(jobLaunchService).findLastExecution(event);
        verify(jobFinalizationService).complete(event,
                PROCESSING_ATTEMPT,
                jobExecution,
                rejectedOutput);
        verify(rejectedRowStagingService).deleteAll(IMPORT_ID);
    }

    @Test
    void processDoesNotFinalizeOrCleanupWhenRejectedOutputUploadFails() throws Exception {
        TransactionImportRequestedEvent event = event();
        IllegalStateException uploadFailure =
                new IllegalStateException("S3 rejected-output upload failed");

        when(jobLaunchService.launch(event, PROCESSING_ATTEMPT)).thenReturn(jobExecution);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(rejectedOutputPreparationService.prepareAndUpload(IMPORT_ID, SOURCE_OBJECT_KEY))
                .thenThrow(uploadFailure);

        assertThatThrownBy(() -> eventProcessor.process(event, PROCESSING_ATTEMPT))
                .isSameAs(uploadFailure);

        verifyNoInteractions(jobFinalizationService, rejectedRowStagingService);
    }

    @Test
    void processDoesNotCleanupWhenFinalizationFails() throws Exception {
        TransactionImportRequestedEvent event = event();
        TransactionImportRejectedOutput rejectedOutput =
                TransactionImportRejectedOutput.uploaded(2, REJECTED_OBJECT_KEY);

        IllegalStateException finalizationFailure =
                new IllegalStateException("Database finalization failed");

        when(jobLaunchService.launch(event, PROCESSING_ATTEMPT)).thenReturn(jobExecution);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(rejectedOutputPreparationService.prepareAndUpload(IMPORT_ID, SOURCE_OBJECT_KEY))
                .thenReturn(rejectedOutput);
        when(jobFinalizationService.complete(event,
                PROCESSING_ATTEMPT,
                jobExecution,
                rejectedOutput))
                .thenThrow(finalizationFailure);

        assertThatThrownBy(() -> eventProcessor.process(event, PROCESSING_ATTEMPT))
                .isSameAs(finalizationFailure);

        verifyNoInteractions(rejectedRowStagingService);
    }

    @Test
    void processStillSucceedsWhenPostFinalizationCleanupFails() throws Exception {
        TransactionImportRequestedEvent event = event();
        TransactionImportRejectedOutput rejectedOutput =
                TransactionImportRejectedOutput.uploaded(2, REJECTED_OBJECT_KEY);

        successfulExecution(event, rejectedOutput);
        when(rejectedRowStagingService.deleteAll(IMPORT_ID))
                .thenThrow(new IllegalStateException("Cleanup database failure"));

        boolean firstCompletion = eventProcessor.process(event, PROCESSING_ATTEMPT);

        assertThat(firstCompletion).isTrue();

        verify(rejectedRowStagingService).deleteAll(IMPORT_ID);
    }

    @Test
    void processDoesNotTreatPersistedAbandonedExecutionAsCompleted() throws Exception {
        TransactionImportRequestedEvent event = event();

        JobInstanceAlreadyCompleteException cause =
                new JobInstanceAlreadyCompleteException("Job instance cannot be restarted");

        String expectedSummary =
                "Transaction import job execution 81 finished with status ABANDONED";

        when(jobLaunchService.launch(event, PROCESSING_ATTEMPT)).thenThrow(cause);
        when(jobLaunchService.findLastExecution(event)).thenReturn(Optional.of(jobExecution));
        when(jobExecution.getStatus()).thenReturn(BatchStatus.ABANDONED);
        when(jobExecution.getId()).thenReturn(JOB_EXECUTION_ID);
        when(jobExecution.getAllFailureExceptions()).thenReturn(List.of());

        assertThatThrownBy(() -> eventProcessor.process(event, PROCESSING_ATTEMPT))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage(expectedSummary);

        verify(jobFinalizationService).fail(event,
                PROCESSING_ATTEMPT,
                jobExecution,
                expectedSummary);
        verifyNoInteractions(rejectedOutputPreparationService, rejectedRowStagingService);
    }

    @Test
    void processRejectsExistingTerminalInstanceWithoutExecutionMetadata() throws Exception {
        TransactionImportRequestedEvent event = event();

        JobInstanceAlreadyCompleteException cause =
                new JobInstanceAlreadyCompleteException("Job instance already completed");

        when(jobLaunchService.launch(event, PROCESSING_ATTEMPT)).thenThrow(cause);
        when(jobLaunchService.findLastExecution(event)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventProcessor.process(event, PROCESSING_ATTEMPT))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage(
                        "Spring Batch reported an existing terminal job instance but no execution metadata was found for import 41"
                )
                .hasCause(cause);

        verifyNoInteractions(rejectedOutputPreparationService,
                jobFinalizationService,
                rejectedRowStagingService);
    }

    @Test
    void processRejectsAlreadyRunningJobExecution() throws Exception {
        TransactionImportRequestedEvent event = event();

        JobExecutionAlreadyRunningException cause =
                new JobExecutionAlreadyRunningException("Job execution is already running");

        when(jobLaunchService.launch(event, PROCESSING_ATTEMPT)).thenThrow(cause);

        assertThatThrownBy(() -> eventProcessor.process(event, PROCESSING_ATTEMPT))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage("Transaction import job is already running for import 41")
                .hasCause(cause);

        verifyNoInteractions(rejectedOutputPreparationService,
                jobFinalizationService,
                rejectedRowStagingService);
    }

    @Test
    void processWrapsOtherJobLaunchFailures() throws Exception {
        TransactionImportRequestedEvent event = event();
        JobRestartException cause = new JobRestartException("Job could not be restarted");

        when(jobLaunchService.launch(event, PROCESSING_ATTEMPT)).thenThrow(cause);

        assertThatThrownBy(() -> eventProcessor.process(event, PROCESSING_ATTEMPT))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage("Failed to launch transaction import job for import 41")
                .hasCause(cause);

        verifyNoInteractions(rejectedOutputPreparationService,
                jobFinalizationService,
                rejectedRowStagingService);
    }

    @Test
    void processMarksFailedExecutionAndPropagatesFailure() throws Exception {
        TransactionImportRequestedEvent event = event();

        String expectedSummary =
                "Transaction import job execution 81 finished with status FAILED: Database unavailable";

        when(jobLaunchService.launch(event, PROCESSING_ATTEMPT)).thenReturn(jobExecution);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobExecution.getId()).thenReturn(JOB_EXECUTION_ID);
        when(jobExecution.getAllFailureExceptions())
                .thenReturn(List.of(new IllegalStateException("Database unavailable")));

        assertThatThrownBy(() -> eventProcessor.process(event, PROCESSING_ATTEMPT))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage(expectedSummary);

        verify(jobFinalizationService).fail(event,
                PROCESSING_ATTEMPT,
                jobExecution,
                expectedSummary);
        verifyNoInteractions(rejectedOutputPreparationService, rejectedRowStagingService);
    }

    @ParameterizedTest
    @EnumSource(value = BatchStatus.class, names = {"STOPPED", "ABANDONED", "UNKNOWN"})
    void processMarksOtherUnsuccessfulTerminalStatusesAsFailed(BatchStatus status) throws Exception {
        TransactionImportRequestedEvent event = event();
        String expectedSummary =
                "Transaction import job execution 81 finished with status " + status;

        when(jobLaunchService.launch(event, PROCESSING_ATTEMPT)).thenReturn(jobExecution);
        when(jobExecution.getStatus()).thenReturn(status);
        when(jobExecution.getId()).thenReturn(JOB_EXECUTION_ID);
        when(jobExecution.getAllFailureExceptions()).thenReturn(List.of());

        assertThatThrownBy(() -> eventProcessor.process(event, PROCESSING_ATTEMPT))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage(expectedSummary);

        verify(jobFinalizationService).fail(event,
                PROCESSING_ATTEMPT,
                jobExecution,
                expectedSummary);
        verifyNoInteractions(rejectedOutputPreparationService, rejectedRowStagingService);
    }

    @ParameterizedTest
    @EnumSource(value = BatchStatus.class, names = {"STARTING", "STARTED", "STOPPING"})
    void processRejectsNonTerminalExecutionWithoutMarkingImportFailed(BatchStatus status)
            throws Exception {
        TransactionImportRequestedEvent event = event();

        when(jobLaunchService.launch(event, PROCESSING_ATTEMPT)).thenReturn(jobExecution);
        when(jobExecution.getStatus()).thenReturn(status);

        assertThatThrownBy(() -> eventProcessor.process(event, PROCESSING_ATTEMPT))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage(
                        "Transaction import job returned before reaching a terminal status for import 41: "
                                + status
                );

        verifyNoInteractions(rejectedOutputPreparationService,
                jobFinalizationService,
                rejectedRowStagingService);
    }

    @Test
    void processRejectsNullEventBeforeUsingDependencies() {
        assertThatThrownBy(() -> eventProcessor.process(null, PROCESSING_ATTEMPT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Transaction import requested event is required");

        verifyNoInteractions(jobLaunchService,
                rejectedOutputPreparationService,
                jobFinalizationService,
                rejectedRowStagingService);
    }

    @Test
    void processRejectsNullProcessingAttemptBeforeUsingDependencies() {
        TransactionImportRequestedEvent event = event();

        assertThatThrownBy(() -> eventProcessor.process(event, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Transaction import processing attempt is required");

        verifyNoInteractions(jobLaunchService,
                rejectedOutputPreparationService,
                jobFinalizationService,
                rejectedRowStagingService);
    }

    @Test
    void processRejectsProcessingAttemptThatDoesNotMatchEvent() {
        TransactionImportRequestedEvent event = event();

        TransactionImportProcessingAttempt mismatchedAttempt =
                new TransactionImportProcessingAttempt(EVENT_ID,
                        99L,
                        ACCOUNT_ID,
                        USER_ID,
                        "worker-b",
                        4L);

        assertThatThrownBy(() -> eventProcessor.process(event, mismatchedAttempt))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage("Transaction import processing attempt does not match event: importId=41");

        verifyNoInteractions(jobLaunchService,
                rejectedOutputPreparationService,
                jobFinalizationService,
                rejectedRowStagingService);
    }

    private void successfulExecution(TransactionImportRequestedEvent event,
                                     TransactionImportRejectedOutput rejectedOutput) throws Exception {
        when(jobLaunchService.launch(event, PROCESSING_ATTEMPT)).thenReturn(jobExecution);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getId()).thenReturn(JOB_EXECUTION_ID);
        when(rejectedOutputPreparationService.prepareAndUpload(IMPORT_ID, SOURCE_OBJECT_KEY))
                .thenReturn(rejectedOutput);
        when(jobFinalizationService.complete(event,
                PROCESSING_ATTEMPT,
                jobExecution,
                rejectedOutput))
                .thenReturn(true);
    }

    private TransactionImportRequestedEvent event() {
        return TransactionImportRequestedEvent.create(EVENT_ID,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                SOURCE_OBJECT_KEY,
                "correlation-123",
                Instant.parse("2026-08-12T12:00:00Z"));
    }
}