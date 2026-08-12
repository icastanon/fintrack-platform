package com.fintrack.workerservice.transactionimport.batch.listener;

import com.fintrack.workerservice.transactionimport.service.TransactionImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

/*
This is a Spring Batch Job listener. It is listening to the job we attach it to.
Different from a Sqs Listener that listens to a SQS queue.
 */
@Component
public class TransactionImportJobLifecycleListener implements JobExecutionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionImportJobLifecycleListener.class);

    private final TransactionImportService transactionImportService;

    public TransactionImportJobLifecycleListener(TransactionImportService transactionImportService) {
        this.transactionImportService = transactionImportService;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        JobParameters parameters = jobExecution.getJobParameters();

        Long importId = parameters.getLong("importId");
        Long accountId = parameters.getLong("accountId");
        Long userId = parameters.getLong("userId");

        transactionImportService.markRunning(importId, accountId, userId);

        LOGGER.info(
                "Started transaction import job: jobExecutionId={}, importId={}, accountId={}, userId={}",
                jobExecution.getId(),
                importId,
                accountId,
                userId
        );
    }
}