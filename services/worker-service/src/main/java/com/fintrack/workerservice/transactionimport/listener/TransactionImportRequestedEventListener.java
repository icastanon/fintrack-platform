package com.fintrack.workerservice.transactionimport.listener;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.exception.UnsupportedTransactionImportRequestedEventVersionException;
import com.fintrack.workerservice.transactionimport.service.TransactionImportRequestedEventProcessor;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class TransactionImportRequestedEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionImportRequestedEventListener.class);

    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private final TransactionImportRequestedEventProcessor eventProcessor;

    public TransactionImportRequestedEventListener(TransactionImportRequestedEventProcessor eventProcessor) {
        this.eventProcessor = eventProcessor;
    }

    @SqsListener("${fintrack.sqs.import-jobs-queue}")
    public void handle(TransactionImportRequestedEvent event) {
        MDC.put(CORRELATION_ID_MDC_KEY, event.getCorrelationId());

        try {
            validateEventVersion(event);

            LOGGER.info(
                    "Received transaction-import request: eventId={}, importId={}, accountId={}, userId={}, sourceObjectKey={}, eventVersion={}, occurredAt={}",
                    event.getEventId(),
                    event.getImportId(),
                    event.getAccountId(),
                    event.getUserId(),
                    event.getSourceObjectKey(),
                    event.getEventVersion(),
                    event.getOccurredAt()
            );

            boolean firstCompletion = eventProcessor.process(event);

            LOGGER.info(
                    "Finished transaction-import request: eventId={}, importId={}, firstCompletion={}",
                    event.getEventId(),
                    event.getImportId(),
                    firstCompletion
            );
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private void validateEventVersion(TransactionImportRequestedEvent event) {
        if (event.getEventVersion() != TransactionImportRequestedEvent.CURRENT_VERSION) {
            throw new UnsupportedTransactionImportRequestedEventVersionException(event.getEventVersion());
        }
    }
}