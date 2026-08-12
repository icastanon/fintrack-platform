package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.entity.TransactionImport;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportRequestMismatchException;
import com.fintrack.workerservice.transactionimport.service.TransactionImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportJobLaunchServiceTest {

    private static final UUID EVENT_ID = UUID.fromString("8fb4e595-dbbc-4b7f-a791-8902bf5d93e1");
    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 22L;
    private static final Long USER_ID = 9L;
    private static final String JOB_NAME = "transactionImportJob";
    private static final String SOURCE_OBJECT_KEY = "imports/9/import-uuid/source.csv";

    @Mock
    private JobOperator jobOperator;

    @Mock
    private Job transactionImportJob;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private TransactionImportService transactionImportService;

    @Mock
    private TransactionImport transactionImport;

    @Mock
    private JobExecution jobExecution;

    @InjectMocks
    private TransactionImportJobLaunchService jobLaunchService;

    @Test
    void launchLoadsAuthoritativeImportBuildsParametersAndStartsJob() throws Exception {
        TransactionImportRequestedEvent event = event(SOURCE_OBJECT_KEY);

        prepareAuthoritativeImport();

        when(jobOperator.start(eq(transactionImportJob), any(JobParameters.class)))
                .thenReturn(jobExecution);

        JobExecution result = jobLaunchService.launch(event);

        assertThat(result).isSameAs(jobExecution);

        ArgumentCaptor<JobParameters> parametersCaptor = ArgumentCaptor.forClass(JobParameters.class);

        verify(jobOperator).start(eq(transactionImportJob), parametersCaptor.capture());

        assertJobParameters(parametersCaptor.getValue());
        verify(transactionImportService).getRequestedImport(IMPORT_ID, ACCOUNT_ID, USER_ID);
        verifyNoInteractions(jobRepository);
    }

    @Test
    void findLastExecutionLoadsPersistedExecutionUsingAuthoritativeParameters() {
        TransactionImportRequestedEvent event = event(SOURCE_OBJECT_KEY);

        prepareAuthoritativeImport();

        when(transactionImportJob.getName()).thenReturn(JOB_NAME);
        when(jobRepository.getLastJobExecution(eq(JOB_NAME), any(JobParameters.class)))
                .thenReturn(jobExecution);

        Optional<JobExecution> result = jobLaunchService.findLastExecution(event);

        assertThat(result).contains(jobExecution);

        ArgumentCaptor<JobParameters> parametersCaptor = ArgumentCaptor.forClass(JobParameters.class);

        verify(jobRepository).getLastJobExecution(eq(JOB_NAME), parametersCaptor.capture());

        assertJobParameters(parametersCaptor.getValue());
        verify(transactionImportService).getRequestedImport(IMPORT_ID, ACCOUNT_ID, USER_ID);
        verifyNoInteractions(jobOperator);
    }

    @Test
    void findLastExecutionReturnsEmptyWhenBatchExecutionDoesNotExist() {
        TransactionImportRequestedEvent event = event(SOURCE_OBJECT_KEY);

        prepareAuthoritativeImport();

        when(transactionImportJob.getName()).thenReturn(JOB_NAME);
        when(jobRepository.getLastJobExecution(eq(JOB_NAME), any(JobParameters.class)))
                .thenReturn(null);

        Optional<JobExecution> result = jobLaunchService.findLastExecution(event);

        assertThat(result).isEmpty();

        verify(transactionImportService).getRequestedImport(IMPORT_ID, ACCOUNT_ID, USER_ID);
        verifyNoInteractions(jobOperator);
    }

    @Test
    void launchRejectsSourceObjectKeyThatDoesNotMatchDatabase() {
        TransactionImportRequestedEvent event = event("imports/9/wrong/source.csv");

        prepareAuthoritativeImportForSourceMismatch();

        assertThatThrownBy(() -> jobLaunchService.launch(event))
                .isInstanceOf(TransactionImportRequestMismatchException.class)
                .hasMessage("Transaction import request does not match authoritative import 41");

        verify(transactionImportService).getRequestedImport(IMPORT_ID, ACCOUNT_ID, USER_ID);
        verifyNoInteractions(jobOperator, jobRepository);
    }

    @Test
    void findLastExecutionRejectsSourceObjectKeyThatDoesNotMatchDatabase() {
        TransactionImportRequestedEvent event = event("imports/9/wrong/source.csv");

        prepareAuthoritativeImportForSourceMismatch();

        assertThatThrownBy(() -> jobLaunchService.findLastExecution(event))
                .isInstanceOf(TransactionImportRequestMismatchException.class)
                .hasMessage("Transaction import request does not match authoritative import 41");

        verify(transactionImportService).getRequestedImport(IMPORT_ID, ACCOUNT_ID, USER_ID);
        verifyNoInteractions(jobOperator, jobRepository);
    }

    @Test
    void launchPropagatesAlreadyRunningException() throws Exception {
        TransactionImportRequestedEvent event = event(SOURCE_OBJECT_KEY);

        prepareAuthoritativeImport();

        JobExecutionAlreadyRunningException cause =
                new JobExecutionAlreadyRunningException("Import job is already running");

        when(jobOperator.start(eq(transactionImportJob), any(JobParameters.class)))
                .thenThrow(cause);

        assertThatThrownBy(() -> jobLaunchService.launch(event)).isSameAs(cause);

        verifyNoInteractions(jobRepository);
    }

    @Test
    void launchRejectsNullEventBeforeUsingDependencies() {
        assertThatThrownBy(() -> jobLaunchService.launch(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Transaction import requested event is required");

        verifyNoInteractions(
                transactionImportService,
                jobOperator,
                jobRepository
        );
    }

    @Test
    void findLastExecutionRejectsNullEventBeforeUsingDependencies() {
        assertThatThrownBy(() -> jobLaunchService.findLastExecution(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Transaction import requested event is required");

        verifyNoInteractions(
                transactionImportService,
                jobOperator,
                jobRepository
        );
    }

    private void prepareAuthoritativeImport() {
        when(transactionImportService.getRequestedImport(IMPORT_ID, ACCOUNT_ID, USER_ID))
                .thenReturn(transactionImport);
        when(transactionImport.getId()).thenReturn(IMPORT_ID);
        when(transactionImport.getAccountId()).thenReturn(ACCOUNT_ID);
        when(transactionImport.getSourceObjectKey()).thenReturn(SOURCE_OBJECT_KEY);
    }

    private void prepareAuthoritativeImportForSourceMismatch() {
        when(transactionImportService.getRequestedImport(IMPORT_ID, ACCOUNT_ID, USER_ID))
                .thenReturn(transactionImport);
        when(transactionImport.getId()).thenReturn(IMPORT_ID);
        when(transactionImport.getSourceObjectKey()).thenReturn(SOURCE_OBJECT_KEY);
    }

    private void assertJobParameters(JobParameters parameters) {
        assertThat(parameters.getLong("importId")).isEqualTo(IMPORT_ID);
        assertThat(parameters.getLong("accountId")).isEqualTo(ACCOUNT_ID);
        assertThat(parameters.getLong("userId")).isEqualTo(USER_ID);
        assertThat(parameters.getString("sourceObjectKey")).isEqualTo(SOURCE_OBJECT_KEY);

        assertThat(parameters.getParameter("importId").identifying()).isTrue();
        assertThat(parameters.getParameter("accountId").identifying()).isFalse();
        assertThat(parameters.getParameter("userId").identifying()).isFalse();
        assertThat(parameters.getParameter("sourceObjectKey").identifying()).isFalse();

        assertThat(parameters.getIdentifyingParameters())
                .containsExactly(parameters.getParameter("importId"));
    }

    private TransactionImportRequestedEvent event(String sourceObjectKey) {
        return TransactionImportRequestedEvent.create(
                EVENT_ID,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                sourceObjectKey,
                "correlation-123",
                Instant.parse("2026-08-12T12:00:00Z")
        );
    }
}