package com.fintrack.workerservice.transaction.service;

import com.fintrack.eventcontracts.TransactionProcessingRequestEvent;
import com.fintrack.workerservice.budget.model.BudgetEvaluationResult;
import com.fintrack.workerservice.budget.service.BudgetEvaluationService;
import com.fintrack.workerservice.category.service.CategorizationService;
import com.fintrack.workerservice.idempotency.service.ProcessedMessageService;
import com.fintrack.workerservice.notification.service.NotificationService;
import com.fintrack.workerservice.transaction.entity.FinancialTransaction;
import com.fintrack.workerservice.transaction.entity.TransactionType;
import com.fintrack.workerservice.transaction.exception.FinancialTransactionNotFoundException;
import com.fintrack.workerservice.transaction.repository.FinancialTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class TransactionProcessingRequestEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionProcessingRequestEventProcessor.class);
    private static final String CONSUMER_NAME = "transaction-processing-request-processor";
    private static final String EVENT_TYPE = "TRANSACTION_PROCESSING_REQUESTED";

    private final ProcessedMessageService processedMessageService;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final CategorizationService categorizationService;
    private final BudgetEvaluationService budgetEvaluationService;
    private final NotificationService notificationService;

    public TransactionProcessingRequestEventProcessor(ProcessedMessageService processedMessageService,
                                                      FinancialTransactionRepository financialTransactionRepository,
                                                      CategorizationService categorizationService,
                                                      BudgetEvaluationService budgetEvaluationService,
                                                      NotificationService notificationService) {
        this.processedMessageService = processedMessageService;
        this.financialTransactionRepository = financialTransactionRepository;
        this.categorizationService = categorizationService;
        this.budgetEvaluationService = budgetEvaluationService;
        this.notificationService = notificationService;
    }

    @Transactional
    public boolean process(TransactionProcessingRequestEvent event) {
        boolean firstProcessing = processedMessageService.recordIfFirst(
                event.getEventId(),
                CONSUMER_NAME,
                EVENT_TYPE,
                event.getEventVersion()
        );

        if (!firstProcessing) {
            LOGGER.info(
                    "Skipping duplicate transaction-processing request: eventId={}, reason={}",
                    event.getEventId(),
                    event.getReason()
            );

            return false;
        }

        FinancialTransaction transaction = financialTransactionRepository
                .findByIdAndUserId(event.getTransactionId(), event.getUserId())
                .orElseThrow(() -> new FinancialTransactionNotFoundException(
                        event.getTransactionId(),
                        event.getUserId()
                ));

        if (!transaction.isManualCategoryOverride()) {
            Long categoryId = categorizationService.categorizeMerchant(transaction.getMerchant());
            transaction.assignAutomaticCategory(categoryId);
        }

        transaction.markProcessed();

        if (transaction.getTransactionType() == TransactionType.EXPENSE) {
            Long categoryId = transaction.getCategoryId();
            LocalDate transactionDate = transaction.getTransactionDate();

            Optional<BudgetEvaluationResult> evaluation = budgetEvaluationService.evaluate(
                    event.getUserId(),
                    categoryId,
                    transactionDate
            );

            evaluation.ifPresent(result ->
                    handleBudgetEvaluation(event, categoryId, transactionDate, result)
            );
        }

        LOGGER.info(
                "Processed transaction-processing request: eventId={}, reason={}, transactionId={}, categoryId={}, manualCategoryOverride={}, processingStatus={}",
                event.getEventId(),
                event.getReason(),
                transaction.getId(),
                transaction.getCategoryId(),
                transaction.isManualCategoryOverride(),
                transaction.getProcessingStatus()
        );

        return true;
    }

    private void handleBudgetEvaluation(TransactionProcessingRequestEvent event, Long categoryId,
                                        LocalDate transactionDate, BudgetEvaluationResult result) {
        logBudgetEvaluation(event, result);

        boolean notificationCreated = notificationService.createIfRequired(
                event.getUserId(),
                categoryId,
                event.getTransactionId(),
                transactionDate,
                result
        );

        if (notificationCreated) {
            LOGGER.info(
                    "Created budget notification: eventId={}, reason={}, budgetId={}, categoryId={}, budgetMonth={}, status={}",
                    event.getEventId(),
                    event.getReason(),
                    result.getBudgetId(),
                    categoryId,
                    transactionDate.withDayOfMonth(1),
                    result.getStatus()
            );
        }
    }

    private void logBudgetEvaluation(TransactionProcessingRequestEvent event, BudgetEvaluationResult result) {
        LOGGER.info(
                "Evaluated budget: eventId={}, reason={}, budgetId={}, status={}, spentAmount={}, budgetAmount={}, usagePercentage={}",
                event.getEventId(),
                event.getReason(),
                result.getBudgetId(),
                result.getStatus(),
                result.getSpentAmount(),
                result.getBudgetAmount(),
                result.getUsagePercentage()
        );
    }
}