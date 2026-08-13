package com.fintrack.workerservice.transactionimport.batch.config;

import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportCsvRow;
import com.fintrack.workerservice.transactionimport.batch.model.ValidatedTransactionImportRow;
import com.fintrack.workerservice.transactionimport.batch.processor.TransactionImportItemProcessor;
import com.fintrack.workerservice.transactionimport.batch.writer.TransactionImportItemWriter;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportRowValidationException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TransactionImportStepConfiguration {

    private static final int CHUNK_SIZE = 100;
    private static final int SKIP_LIMIT = 100;

    @Bean
    public Step transactionImportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<TransactionImportCsvRow> transactionImportCsvReader,
            TransactionImportItemProcessor transactionImportItemProcessor,
            TransactionImportItemWriter transactionImportItemWriter) {
        return new StepBuilder("transactionImportStep", jobRepository)
                .<TransactionImportCsvRow, ValidatedTransactionImportRow>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(transactionImportCsvReader)
                .processor(transactionImportItemProcessor)
                .writer(transactionImportItemWriter)
                .faultTolerant()
                .skip(TransactionImportRowValidationException.class)
                .skipLimit(SKIP_LIMIT)
                .build();
    }
}