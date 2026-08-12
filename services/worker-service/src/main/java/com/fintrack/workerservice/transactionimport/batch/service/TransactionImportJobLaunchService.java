package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.entity.TransactionImport;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportRequestMismatchException;
import com.fintrack.workerservice.transactionimport.service.TransactionImportService;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class TransactionImportJobLaunchService {

    private final JobOperator jobOperator;
    private final Job transactionImportJob;
    private final TransactionImportService transactionImportService;

    public TransactionImportJobLaunchService(JobOperator jobOperator,
                                             @Qualifier("transactionImportJob") Job transactionImportJob,
                                             TransactionImportService transactionImportService) {
        this.jobOperator = jobOperator;
        this.transactionImportJob = transactionImportJob;
        this.transactionImportService = transactionImportService;
    }

    public JobExecution launch(TransactionImportRequestedEvent event) throws JobExecutionException {
        Objects.requireNonNull(event, "Transaction import requested event is required");

        TransactionImport transactionImport = transactionImportService.getRequestedImport(
                event.getImportId(),
                event.getAccountId(),
                event.getUserId()
        );

        validateSourceObjectKey(event, transactionImport);

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("importId", transactionImport.getId(), true)
                .addLong("accountId", transactionImport.getAccountId(), false)
                .addLong("userId", event.getUserId(), false)
                .addString("sourceObjectKey", transactionImport.getSourceObjectKey(), false)
                .toJobParameters();

        return jobOperator.start(transactionImportJob, jobParameters);
    }

    private void validateSourceObjectKey(TransactionImportRequestedEvent event,
                                         TransactionImport transactionImport) {
        if (!transactionImport.getSourceObjectKey().equals(event.getSourceObjectKey())) {
            throw new TransactionImportRequestMismatchException(transactionImport.getId());
        }
    }
}