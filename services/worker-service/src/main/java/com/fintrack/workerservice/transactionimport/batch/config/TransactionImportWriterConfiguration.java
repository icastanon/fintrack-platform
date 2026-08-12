package com.fintrack.workerservice.transactionimport.batch.config;

import com.fintrack.workerservice.account.repository.FinancialAccountRepository;
import com.fintrack.workerservice.budget.service.BudgetEvaluationService;
import com.fintrack.workerservice.notification.service.NotificationService;
import com.fintrack.workerservice.transaction.repository.FinancialTransactionRepository;
import com.fintrack.workerservice.transactionimport.batch.writer.TransactionImportItemWriter;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionImportWriterConfiguration {

    private final FinancialAccountRepository financialAccountRepository;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final BudgetEvaluationService budgetEvaluationService;
    private final NotificationService notificationService;

    public TransactionImportWriterConfiguration(FinancialAccountRepository financialAccountRepository,
                                                FinancialTransactionRepository financialTransactionRepository,
                                                BudgetEvaluationService budgetEvaluationService,
                                                NotificationService notificationService) {
        this.financialAccountRepository = financialAccountRepository;
        this.financialTransactionRepository = financialTransactionRepository;
        this.budgetEvaluationService = budgetEvaluationService;
        this.notificationService = notificationService;
    }

    @Bean
    @StepScope
    public TransactionImportItemWriter transactionImportItemWriter(
            @Value("#{jobParameters['importId']}") Long importId,
            @Value("#{jobParameters['accountId']}") Long accountId,
            @Value("#{jobParameters['userId']}") Long userId) {
        return new TransactionImportItemWriter(
                financialAccountRepository,
                financialTransactionRepository,
                budgetEvaluationService,
                notificationService,
                importId,
                accountId,
                userId);
    }
}