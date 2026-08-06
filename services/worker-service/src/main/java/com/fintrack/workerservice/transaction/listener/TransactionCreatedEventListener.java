package com.fintrack.workerservice.transaction.listener;

import com.fintrack.eventcontracts.TransactionCreatedEvent;
import com.fintrack.workerservice.transaction.exception.UnsupportedTransactionCreatedEventVersionException;
import com.fintrack.workerservice.transaction.service.TransactionCreatedEventProcessor;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TransactionCreatedEventListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TransactionCreatedEventListener.class);

    private final TransactionCreatedEventProcessor transactionCreatedEventProcessor;

    public TransactionCreatedEventListener(TransactionCreatedEventProcessor transactionCreatedEventProcessor) {
        this.transactionCreatedEventProcessor = transactionCreatedEventProcessor;
    }

    @SqsListener("${fintrack.sqs.transaction-processing-queue}")
    public void handle(TransactionCreatedEvent event) {
        validateEventVersion(event);

        LOGGER.info(
                "Received transaction-created event: eventId={}, transactionId={}, userId={}, eventVersion={}, occurredAt={}",
                event.getEventId(),
                event.getTransactionId(),
                event.getUserId(),
                event.getEventVersion(),
                event.getOccurredAt()
        );

        transactionCreatedEventProcessor.process(event);
    }

    private void validateEventVersion(TransactionCreatedEvent event) {
        if (event.getEventVersion() != TransactionCreatedEvent.CURRENT_VERSION) {
            throw new UnsupportedTransactionCreatedEventVersionException(
                    event.getEventVersion()
            );
        }
    }
}