package com.fintrack.workerservice.transactionimport.batch.config;

import com.fintrack.workerservice.transactionimport.batch.listener.TransactionImportSkipListener;
import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportCsvRow;
import com.fintrack.workerservice.transactionimport.batch.model.ValidatedTransactionImportRow;
import com.fintrack.workerservice.transactionimport.batch.processor.TransactionImportItemProcessor;
import com.fintrack.workerservice.transactionimport.batch.stream.TransactionImportChunkCommitFence;
import com.fintrack.workerservice.transactionimport.batch.writer.TransactionImportItemWriter;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportRowValidationException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.ChunkOrientedStepBuilder;
import org.springframework.batch.core.step.skip.LimitCheckingExceptionHierarchySkipPolicy;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.Set;

@Configuration
public class TransactionImportStepConfiguration {

    private static final int CHUNK_SIZE = 100;
    private static final int SKIP_LIMIT = 100;
    private static final int RETRY_LIMIT = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(200);

    @Bean
    public Step transactionImportStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      FlatFileItemReader<TransactionImportCsvRow> transactionImportCsvReader,
                                      TransactionImportItemProcessor transactionImportItemProcessor,
                                      TransactionImportItemWriter transactionImportItemWriter,
                                      TransactionImportChunkCommitFence transactionImportChunkCommitFence,
                                      TransactionImportSkipListener transactionImportSkipListener) {

        SkipPolicy skipPolicy = new LimitCheckingExceptionHierarchySkipPolicy(
                Set.of(TransactionImportRowValidationException.class, FlatFileParseException.class),
                SKIP_LIMIT
        );

        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(RETRY_LIMIT)
                .delay(RETRY_DELAY)
                .includes(TransientDataAccessException.class)
                .build();

        ChunkOrientedStepBuilder<TransactionImportCsvRow, ValidatedTransactionImportRow> stepBuilder =
                new ChunkOrientedStepBuilder<>("transactionImportStep", jobRepository, CHUNK_SIZE);

        return stepBuilder
                .transactionManager(transactionManager)
                .reader(transactionImportCsvReader)
                .processor(transactionImportItemProcessor)
                .writer(transactionImportItemWriter)
                .stream(transactionImportChunkCommitFence)
                .faultTolerant()
                .retryPolicy(retryPolicy)
                .skipPolicy(skipPolicy)
                .skipListener(transactionImportSkipListener)
                .build();
    }
}