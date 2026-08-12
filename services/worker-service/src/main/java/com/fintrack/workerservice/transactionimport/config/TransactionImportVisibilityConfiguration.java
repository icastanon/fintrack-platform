package com.fintrack.workerservice.transactionimport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class TransactionImportVisibilityConfiguration {

    @Bean
    public TaskScheduler transactionImportVisibilityTaskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

        taskScheduler.setPoolSize(2);
        taskScheduler.setThreadNamePrefix("transaction-import-visibility-");
        taskScheduler.setRemoveOnCancelPolicy(true);
        taskScheduler.setWaitForTasksToCompleteOnShutdown(false);

        return taskScheduler;
    }
}