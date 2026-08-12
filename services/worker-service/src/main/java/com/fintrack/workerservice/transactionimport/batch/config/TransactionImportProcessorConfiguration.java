package com.fintrack.workerservice.transactionimport.batch.config;

import com.fintrack.workerservice.category.cache.CategorizationRuleCache;
import com.fintrack.workerservice.category.cache.model.CategorizationRuleCacheSnapshot;
import com.fintrack.workerservice.category.service.CategorizationService;
import com.fintrack.workerservice.transactionimport.batch.processor.TransactionImportItemProcessor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionImportProcessorConfiguration {

    private final CategorizationService categorizationService;
    private final CategorizationRuleCache categorizationRuleCache;

    public TransactionImportProcessorConfiguration(CategorizationService categorizationService,
                                                   CategorizationRuleCache categorizationRuleCache) {
        this.categorizationService = categorizationService;
        this.categorizationRuleCache = categorizationRuleCache;
    }

    @Bean
    @StepScope
    public TransactionImportItemProcessor transactionImportItemProcessor() {
        CategorizationRuleCacheSnapshot snapshot = categorizationRuleCache.getSnapshot();

        return new TransactionImportItemProcessor(categorizationService, snapshot);
    }
}