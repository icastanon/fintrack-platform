package com.fintrack.workerservice.transactionimport.listener;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportJobProcessingException;
import com.fintrack.workerservice.transactionimport.exception.UnsupportedTransactionImportRequestedEventVersionException;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingLeaseAcquisition;
import com.fintrack.workerservice.transactionimport.service.TransactionImportProcessingLeaseManager;
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
    private final TransactionImportProcessingLeaseManager processingLeaseManager;
    private final TransactionImportMessageVisibilityHeartbeat messageVisibilityHeartbeat;

    public TransactionImportRequestedEventListener(TransactionImportRequestedEventProcessor eventProcessor,
                                                   TransactionImportProcessingLeaseManager processingLeaseManager,
                                                   TransactionImportMessageVisibilityHeartbeat messageVisibilityHeartbeat) {
        this.eventProcessor = eventProcessor;
        this.processingLeaseManager = processingLeaseManager;
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

            TransactionImportProcessingLeaseAcquisition acquisition = processingLeaseManager.acquire(event);

            switch (acquisition.getOutcome()) {
                case ALREADY_COMPLETED -> acknowledgeCompletedImport(event);
                case ACTIVE_LEASE -> throw new TransactionImportJobProcessingException(
                        "Transaction import is currently owned by another worker: importId=" + event.getImportId()
                );
                case ACQUIRED -> processAcquiredImport(event, visibility, acquisition.getProcessingAttempt());
            }
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private void processAcquiredImport(TransactionImportRequestedEvent event,
                                       Visibility visibility,
                                       TransactionImportProcessingAttempt processingAttempt) {
        try (TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat =
                     messageVisibilityHeartbeat.start(visibility, processingAttempt)) {
            boolean firstCompletion = eventProcessor.process(event);

            if (runningHeartbeat.hasLostProcessingLease()) {
                throw new TransactionImportJobProcessingException(
                        "Transaction import processing lease was lost: importId=" + event.getImportId()
                );
            }

            LOGGER.info(
                    "Finished transaction-import request: eventId={}, importId={}, firstCompletion={}",
                    event.getEventId(),
                    event.getImportId(),
                    firstCompletion
            );
        }
    }

    private void acknowledgeCompletedImport(TransactionImportRequestedEvent event) {
        LOGGER.info(
                "Acknowledging already-completed transaction-import request: eventId={}, importId={}",
                event.getEventId(),
                event.getImportId()
        );
    }

    private void validateEventVersion(TransactionImportRequestedEvent event) {
        if (event.getEventVersion() != TransactionImportRequestedEvent.CURRENT_VERSION) {
            throw new UnsupportedTransactionImportRequestedEventVersionException(event.getEventVersion());
        }
    }
}