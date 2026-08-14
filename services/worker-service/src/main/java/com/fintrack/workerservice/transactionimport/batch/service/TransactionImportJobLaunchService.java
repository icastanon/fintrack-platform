package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.entity.TransactionImport;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportRequestMismatchException;
import com.fintrack.workerservice.transactionimport.service.TransactionImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class TransactionImportJobLaunchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionImportJobLaunchService.class);

    private final JobOperator jobOperator;
    private final Job transactionImportJob;
    private final JobRepository jobRepository;
    private final TransactionImportService transactionImportService;

    public TransactionImportJobLaunchService(JobOperator jobOperator,
                                             @Qualifier("transactionImportJob") Job transactionImportJob,
                                             JobRepository jobRepository,
                                             TransactionImportService transactionImportService) {
        this.jobOperator = jobOperator;
        this.transactionImportJob = transactionImportJob;
        this.jobRepository = jobRepository;
        this.transactionImportService = transactionImportService;
    }

    public JobExecution launch(TransactionImportRequestedEvent event) throws JobExecutionException {
        JobParameters jobParameters = buildVerifiedJobParameters(event);
        return jobOperator.start(transactionImportJob, jobParameters);
    }

    public boolean recoverLastExecutionIfRunning(TransactionImportRequestedEvent event) {
        Optional<JobExecution> optionalExecution = findLastExecution(event);

        if (optionalExecution.isEmpty()) {
            return false;
        }

        JobExecution jobExecution = optionalExecution.get();

        if (!jobExecution.isRunning()) {
            return false;
        }

        BatchStatus previousStatus = jobExecution.getStatus();
        jobOperator.recover(jobExecution);

        LOGGER.warn(
                "Recovered stale transaction-import job execution: importId={}, jobExecutionId={}, previousStatus={}",
                event.getImportId(),
                jobExecution.getId(),
                previousStatus
        );

        return true;
    }

    public Optional<JobExecution> findLastExecution(TransactionImportRequestedEvent event) {
        JobParameters jobParameters = buildVerifiedJobParameters(event);
        JobExecution jobExecution = jobRepository.getLastJobExecution(transactionImportJob.getName(), jobParameters);

        return Optional.ofNullable(jobExecution);
    }

    private JobParameters buildVerifiedJobParameters(TransactionImportRequestedEvent event) {
        Objects.requireNonNull(event, "Transaction import requested event is required");

        TransactionImport transactionImport = transactionImportService.getRequestedImport(
                event.getImportId(),
                event.getAccountId(),
                event.getUserId()
        );

        validateSourceObjectKey(event, transactionImport);

        return new JobParametersBuilder()
                .addLong("importId", transactionImport.getId(), true)
                .addLong("accountId", transactionImport.getAccountId(), false)
                .addLong("userId", event.getUserId(), false)
                .addString("sourceObjectKey", transactionImport.getSourceObjectKey(), false)
                .toJobParameters();
    }

    private void validateSourceObjectKey(TransactionImportRequestedEvent event,
                                         TransactionImport transactionImport) {
        if (!transactionImport.getSourceObjectKey().equals(event.getSourceObjectKey())) {
            throw new TransactionImportRequestMismatchException(transactionImport.getId());
        }
    }
}