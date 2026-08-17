package com.fintrack.workerservice.transactionimport.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportRejectedOutput;
import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportJobFinalizationService;
import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportJobLaunchService;
import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportRejectedOutputPreparationService;
import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportRejectedRowStagingService;
import com.fintrack.workerservice.transactionimport.batch.validation.TransactionImportJobParametersValidator;
import com.fintrack.workerservice.transactionimport.entity.TransactionImport;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.AbstractItemCountingItemStreamItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(
        classes = TransactionImportProcessorRecoveryIntegrationTest.RecoveryTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.batch.job.enabled=false"
)
@Sql(
        scripts = "classpath:org/springframework/batch/core/schema-postgresql.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
class TransactionImportProcessorRecoveryIntegrationTest {

    private static final UUID EVENT_ID =
            UUID.fromString("83f45747-224e-46c8-811a-143838220f4c");

    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 52L;
    private static final Long USER_ID = 63L;
    private static final String SOURCE_OBJECT_KEY = "imports/63/processor-recovery/source.csv";
    private static final String JOB_NAME = "transactionImportJob";
    private static final String STEP_NAME = "transactionImportStep";
    private static final String CHECKPOINT_KEY = "processorRecoveryItemReader.read.count";
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-17T12:00:00Z");

    private static final TransactionImportProcessingAttempt FIRST_ATTEMPT =
            new TransactionImportProcessingAttempt(EVENT_ID,
                    IMPORT_ID,
                    ACCOUNT_ID,
                    USER_ID,
                    "worker-one",
                    1L);

    private static final TransactionImportProcessingAttempt SECOND_ATTEMPT =
            new TransactionImportProcessingAttempt(EVENT_ID,
                    IMPORT_ID,
                    ACCOUNT_ID,
                    USER_ID,
                    "worker-two",
                    2L);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private TransactionImportRequestedEventProcessor eventProcessor;

    @MockitoBean
    private TransactionImportService transactionImportService;

    @MockitoBean
    private TransactionImportRejectedOutputPreparationService rejectedOutputPreparationService;

    @MockitoBean
    private TransactionImportJobFinalizationService jobFinalizationService;

    @MockitoBean
    private TransactionImportRejectedRowStagingService rejectedRowStagingService;

    private TransactionImportRequestedEvent event;

    @BeforeEach
    void setUp() {
        clearBatchMetadata();

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS processor_recovery_test_item (
                    item_value INTEGER PRIMARY KEY
                )
                """);

        jdbcTemplate.execute("TRUNCATE TABLE processor_recovery_test_item");

        event = createEvent();

        TransactionImport transactionImport = mock(TransactionImport.class);

        when(transactionImportService.getRequestedImport(IMPORT_ID, ACCOUNT_ID, USER_ID))
                .thenReturn(transactionImport);
        when(transactionImport.getId()).thenReturn(IMPORT_ID);
        when(transactionImport.getAccountId()).thenReturn(ACCOUNT_ID);
        when(transactionImport.getSourceObjectKey()).thenReturn(SOURCE_OBJECT_KEY);
    }

    @Test
    void processorRecoversStaleExecutionResumesCheckpointAndFinalizes() {
        insertPreviouslyCommittedItems();

        JobExecution staleExecution = createStaleExecution();
        StepExecution staleStepExecution = getOnlyStepExecution(staleExecution);

        assertThat(staleExecution.getStatus()).isEqualTo(BatchStatus.STARTED);
        assertThat(staleStepExecution.getStatus()).isEqualTo(BatchStatus.STARTED);
        assertPersistedCheckpoint(staleStepExecution, 2);
        assertThat(readPersistedItems()).containsExactly(1, 2);

        TransactionImportRejectedOutput rejectedOutput =
                TransactionImportRejectedOutput.none();

        when(rejectedOutputPreparationService.prepareAndUpload(IMPORT_ID, SOURCE_OBJECT_KEY))
                .thenReturn(rejectedOutput);

        when(jobFinalizationService.complete(
                eq(event),
                eq(SECOND_ATTEMPT),
                any(JobExecution.class),
                same(rejectedOutput)
        )).thenReturn(true);

        when(rejectedRowStagingService.deleteAll(IMPORT_ID)).thenReturn(0);

        boolean firstCompletion = eventProcessor.process(event, SECOND_ATTEMPT);

        assertThat(firstCompletion).isTrue();

        JobExecution recoveredStaleExecution =
                jobRepository.getJobExecution(staleExecution.getId());

        assertThat(recoveredStaleExecution).isNotNull();
        assertThat(recoveredStaleExecution.getStatus()).isEqualTo(BatchStatus.FAILED);

        StepExecution recoveredStaleStepExecution =
                getOnlyStepExecution(recoveredStaleExecution);

        assertThat(recoveredStaleStepExecution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertPersistedCheckpoint(recoveredStaleStepExecution, 2);

        ArgumentCaptor<JobExecution> completedExecutionCaptor =
                ArgumentCaptor.forClass(JobExecution.class);

        verify(jobFinalizationService).complete(
                eq(event),
                eq(SECOND_ATTEMPT),
                completedExecutionCaptor.capture(),
                same(rejectedOutput)
        );

        JobExecution completedExecution = completedExecutionCaptor.getValue();

        assertThat(completedExecution.getId()).isNotEqualTo(staleExecution.getId());
        assertThat(completedExecution.getJobInstanceId())
                .isEqualTo(staleExecution.getJobInstanceId());
        assertThat(completedExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        StepExecution completedStepExecution =
                getOnlyStepExecution(completedExecution);

        assertThat(completedStepExecution.getReadCount()).isEqualTo(2L);
        assertThat(completedStepExecution.getWriteCount()).isEqualTo(2L);

        assertThat(readPersistedItems()).containsExactly(1, 2, 3, 4);
        assertThat(readJobExecutionCount()).isEqualTo(2);

        verify(rejectedOutputPreparationService)
                .prepareAndUpload(IMPORT_ID, SOURCE_OBJECT_KEY);

        verify(rejectedRowStagingService).deleteAll(IMPORT_ID);
    }

    private JobExecution createStaleExecution() {
        JobParameters jobParameters = buildJobParameters(FIRST_ATTEMPT);
        JobInstance jobInstance = jobRepository.createJobInstance(JOB_NAME, jobParameters);

        JobExecution jobExecution =
                jobRepository.createJobExecution(
                        jobInstance,
                        jobParameters,
                        new ExecutionContext()
                );

        jobExecution.setStatus(BatchStatus.STARTED);
        jobExecution.setStartTime(LocalDateTime.now());
        jobExecution.setExitStatus(ExitStatus.EXECUTING);
        jobRepository.update(jobExecution);

        StepExecution stepExecution =
                jobRepository.createStepExecution(STEP_NAME, jobExecution);

        stepExecution.setStatus(BatchStatus.STARTED);
        stepExecution.setStartTime(LocalDateTime.now());
        stepExecution.setExitStatus(ExitStatus.EXECUTING);
        stepExecution.setCommitCount(1L);
        stepExecution.setReadCount(2L);
        stepExecution.setWriteCount(2L);
        stepExecution.getExecutionContext().putInt(CHECKPOINT_KEY, 2);

        jobRepository.update(stepExecution);
        jobRepository.updateExecutionContext(stepExecution);

        return jobRepository.getJobExecution(jobExecution.getId());
    }

    private JobParameters buildJobParameters(TransactionImportProcessingAttempt processingAttempt) {
        return new JobParametersBuilder()
                .addLong("importId", IMPORT_ID, true)
                .addLong("accountId", ACCOUNT_ID, false)
                .addLong("userId", USER_ID, false)
                .addString("sourceObjectKey", SOURCE_OBJECT_KEY, false)
                .addString("processingOwner", processingAttempt.getProcessingOwner(), false)
                .addLong("processingFencingToken", processingAttempt.getFencingToken(), false)
                .toJobParameters();
    }

    private void insertPreviouslyCommittedItems() {
        jdbcTemplate.update("""
                INSERT INTO processor_recovery_test_item (
                    item_value
                )
                VALUES (1), (2)
                """);
    }

    private void assertPersistedCheckpoint(StepExecution stepExecution, int expectedCount) {
        StepExecution persistedStepExecution =
                jobRepository.getStepExecution(stepExecution.getId());

        assertThat(persistedStepExecution).isNotNull();

        assertThat(
                persistedStepExecution.getExecutionContext().containsKey(CHECKPOINT_KEY)
        ).isTrue();

        assertThat(
                persistedStepExecution.getExecutionContext().getInt(CHECKPOINT_KEY)
        ).isEqualTo(expectedCount);
    }

    private StepExecution getOnlyStepExecution(JobExecution jobExecution) {
        assertThat(jobExecution.getStepExecutions()).hasSize(1);
        return jobExecution.getStepExecutions().iterator().next();
    }

    private List<Integer> readPersistedItems() {
        return jdbcTemplate.queryForList(
                "SELECT item_value FROM processor_recovery_test_item ORDER BY item_value",
                Integer.class
        );
    }

    private int readJobExecutionCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM batch_job_execution",
                Integer.class
        );

        return count == null ? 0 : count;
    }

    private void clearBatchMetadata() {
        jdbcTemplate.execute("DELETE FROM batch_step_execution_context");
        jdbcTemplate.execute("DELETE FROM batch_step_execution");
        jdbcTemplate.execute("DELETE FROM batch_job_execution_context");
        jdbcTemplate.execute("DELETE FROM batch_job_execution_params");
        jdbcTemplate.execute("DELETE FROM batch_job_execution");
        jdbcTemplate.execute("DELETE FROM batch_job_instance");
    }

    private TransactionImportRequestedEvent createEvent() {
        return TransactionImportRequestedEvent.create(
                EVENT_ID,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                SOURCE_OBJECT_KEY,
                "processor-recovery-integration-test",
                OCCURRED_AT
        );
    }

    @EnableBatchProcessing
    @EnableJdbcJobRepository
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            TransactionAutoConfiguration.class
    })
    @Import({
            TransactionImportJobLaunchService.class,
            TransactionImportJobParametersValidator.class,
            TransactionImportRequestedEventProcessor.class
    })
    static class RecoveryTestApplication {

        @Bean
        CheckpointItemReader processorRecoveryItemReader() {
            return new CheckpointItemReader(List.of(1, 2, 3, 4));
        }

        @Bean
        ItemWriter<Integer> processorRecoveryItemWriter(JdbcTemplate jdbcTemplate) {
            return chunk -> {
                for (Integer item : chunk.getItems()) {
                    jdbcTemplate.update(
                            "INSERT INTO processor_recovery_test_item (item_value) VALUES (?)",
                            item
                    );
                }
            };
        }

        @Bean
        Step transactionImportStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   CheckpointItemReader processorRecoveryItemReader,
                                   ItemWriter<Integer> processorRecoveryItemWriter) {
            return new StepBuilder(STEP_NAME, jobRepository)
                    .<Integer, Integer>chunk(2)
                    .transactionManager(transactionManager)
                    .reader(processorRecoveryItemReader)
                    .writer(processorRecoveryItemWriter)
                    .build();
        }

        @Bean
        Job transactionImportJob(JobRepository jobRepository,
                                 @Qualifier("transactionImportStep") Step transactionImportStep,
                                 TransactionImportJobParametersValidator jobParametersValidator) {
            return new JobBuilder(JOB_NAME, jobRepository)
                    .validator(jobParametersValidator)
                    .start(transactionImportStep)
                    .build();
        }
    }

    static class CheckpointItemReader
            extends AbstractItemCountingItemStreamItemReader<Integer> {

        private final List<Integer> items;
        private int currentIndex;

        CheckpointItemReader(List<Integer> items) {
            this.items = items;
            setName("processorRecoveryItemReader");
        }

        @Override
        protected Integer doRead() {
            if (currentIndex >= items.size()) {
                return null;
            }

            return items.get(currentIndex++);
        }

        @Override
        protected void doOpen() {
            currentIndex = 0;
        }

        @Override
        protected void doClose() {
        }

        @Override
        protected void jumpToItem(int itemIndex) {
            currentIndex = itemIndex;
        }
    }
}