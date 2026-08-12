package com.fintrack.workerservice.transactionimport.listener;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.exception.UnsupportedTransactionImportRequestedEventVersionException;
import com.fintrack.workerservice.transactionimport.service.TransactionImportRequestedEventProcessor;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class TransactionImportRequestedEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionImportRequestedEventListener.class);
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private final TransactionImportRequestedEventProcessor eventProcessor;
    private final TransactionImportMessageVisibilityHeartbeat messageVisibilityHeartbeat;

    public TransactionImportRequestedEventListener(TransactionImportRequestedEventProcessor eventProcessor,
                                                   TransactionImportMessageVisibilityHeartbeat messageVisibilityHeartbeat) {
        this.eventProcessor = eventProcessor;
        this.messageVisibilityHeartbeat = messageVisibilityHeartbeat;
    }

    @SqsListener("${fintrack.sqs.import-jobs-queue}")
    public void handle(TransactionImportRequestedEvent event, Visibility visibility) {
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

            try (TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat =
                         messageVisibilityHeartbeat.start(visibility, event.getEventId(), event.getImportId())) {
                boolean firstCompletion = eventProcessor.process(event);

                LOGGER.info(
                        "Finished transaction-import request: eventId={}, importId={}, firstCompletion={}",
                        event.getEventId(),
                        event.getImportId(),
                        firstCompletion
                );
            }
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