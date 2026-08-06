package com.fintrack.workerservice.transaction.service;

import com.fintrack.eventcontracts.TransactionCreatedEvent;
import com.fintrack.workerservice.idempotency.service.ProcessedMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionCreatedEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionCreatedEventProcessor.class);

    private static final String CONSUMER_NAME = "transaction-created-processor";
    private static final String EVENT_TYPE = "TRANSACTION_CREATED";

    private final ProcessedMessageService processedMessageService;

    public TransactionCreatedEventProcessor(ProcessedMessageService processedMessageService) {
        this.processedMessageService = processedMessageService;
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

        LOGGER.info(
                "Registered transaction-created event for processing: eventId={}",
                event.getEventId()
        );

        /*
         * Future business operations will be added here.
         *
         * Because this method owns the transaction, the processed_message
         * marker and all business changes will commit or roll back together.
         */

        return true;
    }
}