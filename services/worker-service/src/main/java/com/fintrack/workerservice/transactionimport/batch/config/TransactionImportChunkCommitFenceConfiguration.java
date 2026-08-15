package com.fintrack.workerservice.transactionimport.batch.config;

import com.fintrack.workerservice.transactionimport.batch.stream.TransactionImportChunkCommitFence;
import com.fintrack.workerservice.transactionimport.service.TransactionImportProcessingLeaseManager;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionImportChunkCommitFenceConfiguration {

    private final TransactionImportProcessingLeaseManager processingLeaseManager;

    public TransactionImportChunkCommitFenceConfiguration(TransactionImportProcessingLeaseManager processingLeaseManager) {
        this.processingLeaseManager = processingLeaseManager;
    }

    @Bean
    @StepScope
    public TransactionImportChunkCommitFence transactionImportChunkCommitFence(
            @Value("#{jobParameters['importId']}") Long importId,
            @Value("#{jobParameters['accountId']}") Long accountId,
            @Value("#{jobParameters['userId']}") Long userId,
            @Value("#{jobParameters['processingOwner']}") String processingOwner,
            @Value("#{jobParameters['processingFencingToken']}") Long processingFencingToken) {

        return new TransactionImportChunkCommitFence(
                processingLeaseManager,
                importId,
                accountId,
                userId,
                processingOwner,
                processingFencingToken
        );
    }
}