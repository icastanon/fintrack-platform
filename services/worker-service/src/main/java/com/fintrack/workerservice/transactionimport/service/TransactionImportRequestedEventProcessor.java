package com.fintrack.workerservice.transactionimport.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportJobFinalizationService;
import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportJobLaunchService;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportJobProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class TransactionImportRequestedEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionImportRequestedEventProcessor.class);

    private final TransactionImportJobLaunchService jobLaunchService;
    private final TransactionImportJobFinalizationService jobFinalizationService;

    public TransactionImportRequestedEventProcessor(TransactionImportJobLaunchService jobLaunchService,
                                                    TransactionImportJobFinalizationService jobFinalizationService) {
        this.jobLaunchService = jobLaunchService;
        this.jobFinalizationService = jobFinalizationService;
    }

    public boolean process(TransactionImportRequestedEvent event) {
        Objects.requireNonNull(event, "Transaction import requested event is required");

        try {
            JobExecution jobExecution = jobLaunchService.launch(event);
            return handleExecutionResult(event, jobExecution);
        } catch (JobInstanceAlreadyCompleteException exception) {
            return handleExistingTerminalJob(event, exception);
        } catch (JobExecutionAlreadyRunningException exception) {
            throw new TransactionImportJobProcessingException(
                    "Transaction import job is already running for import " + event.getImportId(),
                    exception
            );
        } catch (JobExecutionException exception) {
            throw new TransactionImportJobProcessingException(
                    "Failed to launch transaction import job for import " + event.getImportId(),
                    exception
            );
        }
    }

    private boolean handleExistingTerminalJob(TransactionImportRequestedEvent event,
                                              JobInstanceAlreadyCompleteException cause) {
        JobExecution jobExecution = jobLaunchService.findLastExecution(event)
                .orElseThrow(() -> new TransactionImportJobProcessingException(
                        "Spring Batch reported an existing terminal job instance but no execution metadata was found for import "
                                + event.getImportId(),
                        cause
                ));

        return handleExecutionResult(event, jobExecution);
    }

    private boolean handleExecutionResult(TransactionImportRequestedEvent event,
                                          JobExecution jobExecution) {
        BatchStatus status = jobExecution.getStatus();

        return switch (status) {
            case COMPLETED -> complete(event, jobExecution);
            case FAILED, STOPPED, ABANDONED, UNKNOWN -> handleUnsuccessfulExecution(event, jobExecution);
            case STARTING, STARTED, STOPPING ->
                    throw new TransactionImportJobProcessingException(
                            "Transaction import job returned before reaching a terminal status for import "
                                    + event.getImportId() + ": " + status
                    );
        };
    }

    private boolean complete(TransactionImportRequestedEvent event, JobExecution jobExecution) {
        boolean firstCompletion = jobFinalizationService.complete(event);

        LOGGER.info(
                "Finalized completed transaction import: eventId={}, importId={}, jobExecutionId={}, firstCompletion={}",
                event.getEventId(),
                event.getImportId(),
                jobExecution.getId(),
                firstCompletion
        );

        return firstCompletion;
    }

    private boolean handleUnsuccessfulExecution(TransactionImportRequestedEvent event,
                                                JobExecution jobExecution) {
        String failureSummary = buildFailureSummary(jobExecution);

        jobFinalizationService.fail(event, failureSummary);

        throw new TransactionImportJobProcessingException(failureSummary);
    }

    private String buildFailureSummary(JobExecution jobExecution) {
        String summary = "Transaction import job execution " + jobExecution.getId()
                + " finished with status " + jobExecution.getStatus();

        return jobExecution.getAllFailureExceptions()
                .stream()
                .map(this::failureDescription)
                .filter(description -> !description.isBlank())
                .findFirst()
                .map(description -> summary + ": " + description)
                .orElse(summary);
    }

    private String failureDescription(Throwable failure) {
        if (failure.getMessage() == null || failure.getMessage().isBlank()) {
            return failure.getClass().getSimpleName();
        }

        return failure.getMessage();
    }
}