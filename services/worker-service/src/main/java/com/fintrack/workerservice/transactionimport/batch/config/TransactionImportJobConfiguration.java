package com.fintrack.workerservice.transactionimport.batch.config;

import com.fintrack.workerservice.transactionimport.batch.validation.TransactionImportJobParametersValidator;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionImportJobConfiguration {

    @Bean
    public Job transactionImportJob(JobRepository jobRepository,
                                    @Qualifier("transactionImportStep") Step transactionImportStep,
                                    TransactionImportJobParametersValidator jobParametersValidator) {
        return new JobBuilder("transactionImportJob", jobRepository)
                .validator(jobParametersValidator)
                .start(transactionImportStep)
                .build();
    }
}