package com.fintrack.workerservice.transactionimport.batch.listener;

import com.fintrack.workerservice.transactionimport.service.TransactionImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionImportJobLifecycleListenerTest {

    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 22L;
    private static final Long USER_ID = 9L;

    @Mock
    private TransactionImportService transactionImportService;

    @InjectMocks
    private TransactionImportJobLifecycleListener jobLifecycleListener;

    @Test
    void beforeJobMarksRequestedImportRunning() {
        JobParameters parameters = new JobParametersBuilder()
                .addLong("importId", IMPORT_ID, true)
                .addLong("accountId", ACCOUNT_ID, false)
                .addLong("userId", USER_ID, false)
                .addString("sourceObjectKey", "imports/9/import-uuid/source.csv", false)
                .toJobParameters();

        JobInstance jobInstance = new JobInstance(71L, "transactionImportJob");
        JobExecution jobExecution = new JobExecution(81L, jobInstance, parameters);

        jobLifecycleListener.beforeJob(jobExecution);

        verify(transactionImportService).markRunning(IMPORT_ID, ACCOUNT_ID, USER_ID);
    }
}