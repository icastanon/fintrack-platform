package com.fintrack.workerservice.transaction.listener;

import com.fintrack.eventcontracts.TransactionProcessingRequestEvent;
import com.fintrack.workerservice.transaction.exception.UnsupportedTransactionProcessingRequestEventVersionException;
import com.fintrack.workerservice.transaction.service.TransactionProcessingRequestEventProcessor;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TransactionProcessingRequestEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionProcessingRequestEventListener.class);

    private final TransactionProcessingRequestEventProcessor transactionProcessingRequestEventProcessor;

    public TransactionProcessingRequestEventListener(TransactionProcessingRequestEventProcessor transactionProcessingRequestEventProcessor) {
        this.transactionProcessingRequestEventProcessor = transactionProcessingRequestEventProcessor;
    }

    @SqsListener("${fintrack.sqs.transaction-processing-queue}")
    public void handle(TransactionProcessingRequestEvent event) {
        validateEventVersion(event);

        LOGGER.info(
                "Received transaction-processing request: eventId={}, transactionId={}, userId={}, reason={}, eventVersion={}, occurredAt={}",
                event.getEventId(),
                event.getTransactionId(),
                event.getUserId(),
                event.getReason(),
                event.getEventVersion(),
                event.getOccurredAt()
        );

        transactionProcessingRequestEventProcessor.process(event);
    }

    private void validateEventVersion(TransactionProcessingRequestEvent event) {
        if (event.getEventVersion() != TransactionProcessingRequestEvent.CURRENT_VERSION) {
            throw new UnsupportedTransactionProcessingRequestEventVersionException(event.getEventVersion());
        }
    }
}