package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.batch.validation.TransactionImportJobParametersValidator;
import com.fintrack.workerservice.transactionimport.entity.TransactionImport;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import com.fintrack.workerservice.transactionimport.service.TransactionImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.AbstractItemCountingItemStreamItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(
        classes = TransactionImportStaleExecutionRecoveryIntegrationTest.RecoveryTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.batch.job.enabled=false"
)
@Sql(
        scripts = "classpath:org/springframework/batch/core/schema-postgresql.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS
)
class TransactionImportStaleExecutionRecoveryIntegrationTest {

    private static final UUID EVENT_ID = UUID.fromString("83f45747-224e-46c8-811a-143838220f4c");
    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 52L;
    private static final Long USER_ID = 63L;
    private static final String SOURCE_OBJECT_KEY = "imports/63/recovery/source.csv";
    private static final String JOB_NAME = "transactionImportJob";
    private static final String STEP_NAME = "transactionImportStep";
    private static final String CHECKPOINT_KEY = "recoveryItemReader.read.count";
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-17T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private TransactionImportJobLaunchService jobLaunchService;

    @Autowired
    private AtomicBoolean failSecondChunk;

    @MockitoBean
    private TransactionImportService transactionImportService;

    private TransactionImportRequestedEvent event;

    @BeforeEach
    void setUp() {
        clearBatchMetadata();

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS recovery_test_item (
                    item_value INTEGER PRIMARY KEY
                )
                """);

        jdbcTemplate.execute("TRUNCATE TABLE recovery_test_item");

        failSecondChunk.set(true);
        event = createEvent();

        TransactionImport transactionImport = mock(TransactionImport.class);

        when(transactionImportService.getRequestedImport(IMPORT_ID, ACCOUNT_ID, USER_ID))
                .thenReturn(transactionImport);
        when(transactionImport.getId()).thenReturn(IMPORT_ID);
        when(transactionImport.getAccountId()).thenReturn(ACCOUNT_ID);
        when(transactionImport.getSourceObjectKey()).thenReturn(SOURCE_OBJECT_KEY);
    }

    @Test
    void failedExecutionLaunchResumesFromLastCommittedCheckpoint() throws Exception {
        TransactionImportProcessingAttempt firstAttempt = createProcessingAttempt("worker-one", 1L);
        JobExecution failedExecution = jobLaunchService.launch(event, firstAttempt);

        assertThat(failedExecution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(readPersistedItems()).containsExactly(1, 2);

        StepExecution failedStepExecution = getOnlyStepExecution(failedExecution);

        assertThat(failedStepExecution.getCommitCount()).isEqualTo(1L);
        assertThat(failedStepExecution.getWriteCount()).isEqualTo(2L);
        assertThat(failedStepExecution.getRollbackCount()).isGreaterThanOrEqualTo(1L);
        assertPersistedCheckpoint(failedStepExecution, 2);

        TransactionImportProcessingAttempt secondAttempt = createProcessingAttempt("worker-two", 2L);
        JobExecution restartedExecution = jobLaunchService.launch(event, secondAttempt);

        assertThat(restartedExecution.getId()).isNotEqualTo(failedExecution.getId());
        assertThat(restartedExecution.getJobInstanceId()).isEqualTo(failedExecution.getJobInstanceId());
        assertThat(restartedExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        StepExecution restartedStepExecution = getOnlyStepExecution(restartedExecution);

        assertThat(restartedStepExecution.getReadCount()).isEqualTo(2L);
        assertThat(restartedStepExecution.getWriteCount()).isEqualTo(2L);
        assertThat(readPersistedItems()).containsExactly(1, 2, 3, 4);
        assertThat(readJobExecutionCount()).isEqualTo(2);
    }

    @Test
    void recoverStaleExecutionMarksItFailedAndPreservesCheckpoint() {
        TransactionImportProcessingAttempt processingAttempt =
                createProcessingAttempt("worker-one", 1L);

        JobExecution staleExecution = createStaleExecution(processingAttempt);
        StepExecution staleStepExecution = getOnlyStepExecution(staleExecution);

        assertThat(staleExecution.getStatus()).isEqualTo(BatchStatus.STARTED);
        assertThat(staleStepExecution.getStatus()).isEqualTo(BatchStatus.STARTED);
        assertPersistedCheckpoint(staleStepExecution, 2);

        boolean recovered = jobLaunchService.recoverLastExecutionIfRunning(event);

        assertThat(recovered).isTrue();

        JobExecution recoveredExecution = jobRepository.getJobExecution(staleExecution.getId());

        assertThat(recoveredExecution).isNotNull();
        assertThat(recoveredExecution.getStatus()).isEqualTo(BatchStatus.FAILED);

        StepExecution recoveredStepExecution = getOnlyStepExecution(recoveredExecution);

        assertThat(recoveredStepExecution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertPersistedCheckpoint(recoveredStepExecution, 2);
        assertThat(readJobExecutionCount()).isEqualTo(1);
        assertThat(jobLaunchService.recoverLastExecutionIfRunning(event)).isFalse();
    }

    private JobExecution createStaleExecution(TransactionImportProcessingAttempt processingAttempt) {
        JobParameters jobParameters = buildJobParameters(processingAttempt);
        JobInstance jobInstance = jobRepository.createJobInstance(JOB_NAME, jobParameters);
        JobExecution jobExecution =
                jobRepository.createJobExecution(jobInstance, jobParameters, new ExecutionContext());

        jobExecution.setStatus(BatchStatus.STARTED);
        jobExecution.setStartTime(LocalDateTime.now());
        jobExecution.setExitStatus(ExitStatus.EXECUTING);
        jobRepository.update(jobExecution);

        StepExecution stepExecution = jobRepository.createStepExecution(STEP_NAME, jobExecution);

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

    private void assertPersistedCheckpoint(StepExecution stepExecution, int expectedCount) {
        StepExecution persistedStepExecution = jobRepository.getStepExecution(stepExecution.getId());

        assertThat(persistedStepExecution).isNotNull();
        assertThat(persistedStepExecution.getExecutionContext().containsKey(CHECKPOINT_KEY)).isTrue();
        assertThat(persistedStepExecution.getExecutionContext().getInt(CHECKPOINT_KEY))
                .isEqualTo(expectedCount);
    }

    private StepExecution getOnlyStepExecution(JobExecution jobExecution) {
        assertThat(jobExecution.getStepExecutions()).hasSize(1);
        return jobExecution.getStepExecutions().iterator().next();
    }

    private List<Integer> readPersistedItems() {
        return jdbcTemplate.queryForList(
                "SELECT item_value FROM recovery_test_item ORDER BY item_value",
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

    private TransactionImportProcessingAttempt createProcessingAttempt(String processingOwner,
                                                                       long fencingToken) {
        return new TransactionImportProcessingAttempt(
                EVENT_ID,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                processingOwner,
                fencingToken
        );
    }

    private TransactionImportRequestedEvent createEvent() {
        return TransactionImportRequestedEvent.create(
                EVENT_ID,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                SOURCE_OBJECT_KEY,
                "recovery-integration-test",
                OCCURRED_AT
        );
    }

    @EnableBatchProcessing
    @EnableJdbcJobRepository
    @SpringBootConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            TransactionAutoConfiguration.class
    })
    @Import({
            TransactionImportJobLaunchService.class,
            TransactionImportJobParametersValidator.class
    })
    static class RecoveryTestApplication {

        @Bean
        AtomicBoolean failSecondChunk() {
            return new AtomicBoolean(true);
        }

        @Bean
        CheckpointItemReader recoveryItemReader() {
            return new CheckpointItemReader(List.of(1, 2, 3, 4));
        }

        @Bean
        ItemWriter<Integer> recoveryItemWriter(JdbcTemplate jdbcTemplate,
                                               AtomicBoolean failSecondChunk) {
            return chunk -> {
                failOnSecondChunkOnce(chunk, failSecondChunk);

                for (Integer item : chunk.getItems()) {
                    jdbcTemplate.update(
                            "INSERT INTO recovery_test_item (item_value) VALUES (?)",
                            item
                    );
                }
            };
        }

        @Bean
        Step transactionImportStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   CheckpointItemReader recoveryItemReader,
                                   ItemWriter<Integer> recoveryItemWriter) {
            return new StepBuilder(STEP_NAME, jobRepository)
                    .<Integer, Integer>chunk(2)
                    .transactionManager(transactionManager)
                    .reader(recoveryItemReader)
                    .writer(recoveryItemWriter)
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

        private static void failOnSecondChunkOnce(Chunk<? extends Integer> chunk,
                                                  AtomicBoolean failSecondChunk) {
            if (chunk.getItems().contains(3) && failSecondChunk.compareAndSet(true, false)) {
                throw new IllegalStateException(
                        "Simulated failure after the first committed chunk"
                );
            }
        }
    }

    static class CheckpointItemReader extends AbstractItemCountingItemStreamItemReader<Integer> {

        private final List<Integer> items;
        private int currentIndex;

        CheckpointItemReader(List<Integer> items) {
            this.items = items;
            setName("recoveryItemReader");
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