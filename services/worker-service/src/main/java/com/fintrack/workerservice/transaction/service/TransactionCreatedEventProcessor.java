package com.fintrack.workerservice.transaction.service;

import com.fintrack.eventcontracts.TransactionCreatedEvent;
import com.fintrack.workerservice.category.service.CategorizationService;
import com.fintrack.workerservice.idempotency.service.ProcessedMessageService;
import com.fintrack.workerservice.transaction.entity.FinancialTransaction;
import com.fintrack.workerservice.transaction.exception.FinancialTransactionNotFoundException;
import com.fintrack.workerservice.transaction.repository.FinancialTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionCreatedEventProcessor {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TransactionCreatedEventProcessor.class);

    private static final String CONSUMER_NAME =
            "transaction-created-processor";

    private static final String EVENT_TYPE =
            "TRANSACTION_CREATED";

    private final ProcessedMessageService processedMessageService;
    private final FinancialTransactionRepository financialTransactionRepository;
    private final CategorizationService categorizationService;

    public TransactionCreatedEventProcessor(ProcessedMessageService processedMessageService,
                                            FinancialTransactionRepository financialTransactionRepository,
                                            CategorizationService categorizationService) {
        this.processedMessageService = processedMessageService;
        this.financialTransactionRepository = financialTransactionRepository;
        this.categorizationService = categorizationService;
    }

    @Transactional
    public boolean process(TransactionCreatedEvent event) {
        boolean firstProcessing = processedMessageService.recordIfFirst(
                event.getEventId(),
                CONSUMER_NAME,
                EVENT_TYPE,
                event.getEventVersion()
        );

        if (!firstProcessing) {
            LOGGER.info(
                    "Skipping duplicate transaction-created event: eventId={}",
                    event.getEventId()
            );

            return false;
        }

        FinancialTransaction transaction = financialTransactionRepository
                .findByIdAndUserId(event.getTransactionId(), event.getUserId())
                .orElseThrow(() ->
                        new FinancialTransactionNotFoundException(event.getTransactionId(), event.getUserId())
                );

        if (!transaction.isManualCategoryOverride()) {
            Long categoryId = categorizationService.categorizeMerchant(transaction.getMerchant());

            transaction.assignAutomaticCategory(categoryId);
        }

        transaction.markProcessed();

        LOGGER.info(
                "Processed transaction-created event: eventId={}, transactionId={}, categoryId={}, manualCategoryOverride={}, processingStatus={}",
                event.getEventId(),
                transaction.getId(),
                transaction.getCategoryId(),
                transaction.isManualCategoryOverride(),
                transaction.getProcessingStatus()
        );

        return true;
    }
}