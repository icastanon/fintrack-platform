package com.fintrack.workerservice.transactionimport.batch.config;

import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportCsvRow;
import com.fintrack.workerservice.transactionimport.batch.reader.TransactionImportCsvHeaderValidator;
import com.fintrack.workerservice.transactionimport.batch.reader.TransactionImportCsvLineMapper;
import com.fintrack.workerservice.transactionimport.storage.TransactionImportStorageService;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.InputStreamSource;

import java.nio.charset.StandardCharsets;

@Configuration
public class TransactionImportReaderConfiguration {

    private final TransactionImportStorageService transactionImportStorageService;
    private final TransactionImportCsvHeaderValidator headerValidator;
    private final TransactionImportCsvLineMapper lineMapper;

    public TransactionImportReaderConfiguration(TransactionImportStorageService transactionImportStorageService,
                                                TransactionImportCsvHeaderValidator headerValidator,
                                                TransactionImportCsvLineMapper lineMapper) {
        this.transactionImportStorageService = transactionImportStorageService;
        this.headerValidator = headerValidator;
        this.lineMapper = lineMapper;
    }

    @Bean
    @StepScope
    public FlatFileItemReader<TransactionImportCsvRow> transactionImportCsvReader(
            @Value("#{jobParameters['sourceObjectKey']}") String sourceObjectKey) {
        InputStreamSource inputStreamSource =
                () -> transactionImportStorageService.openSource(sourceObjectKey);

        InputStreamResource sourceResource =
                new InputStreamResource(inputStreamSource, "S3 transaction import source " + sourceObjectKey);

        return new FlatFileItemReaderBuilder<TransactionImportCsvRow>()
                .name("transactionImportCsvReader")
                .resource(sourceResource)
                .encoding(StandardCharsets.UTF_8.name())
                .strict(true)
                .linesToSkip(1)
                .skippedLinesCallback(headerValidator)
                .lineMapper(lineMapper)
                .saveState(true)
                .build();
    }
}