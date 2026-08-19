package com.fintrack.workerservice.transactionimport.listener;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportJobProcessingException;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportProcessingLeaseLostException;
import com.fintrack.workerservice.transactionimport.exception.UnsupportedTransactionImportRequestedEventVersionException;
import com.fintrack.workerservice.transactionimport.metrics.TransactionImportMetrics;
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
    private final TransactionImportMetrics transactionImportMetrics;

    public TransactionImportRequestedEventListener(
            TransactionImportRequestedEventProcessor eventProcessor,
            TransactionImportProcessingLeaseManager processingLeaseManager,
            TransactionImportMessageVisibilityHeartbeat messageVisibilityHeartbeat,
            TransactionImportMetrics transactionImportMetrics) {
        this.eventProcessor = eventProcessor;
        this.processingLeaseManager = processingLeaseManager;
        this.messageVisibilityHeartbeat = messageVisibilityHeartbeat;
        this.transactionImportMetrics = transactionImportMetrics;
    }

    @SqsListener(
            value = "${fintrack.sqs.import-jobs-queue}",
            maxConcurrentMessages = "${fintrack.sqs.import-jobs-max-concurrent-messages}",
            maxMessagesPerPoll = "${fintrack.sqs.import-jobs-max-messages-per-poll}"
    )
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
                case ALREADY_COMPLETED -> handleAlreadyCompletedImport(event);
                case ACTIVE_LEASE -> handleActiveLease(event);
                case ACQUIRED -> handleAcquiredImport(event, visibility, acquisition.getProcessingAttempt());
                case ALREADY_ABANDONED -> handleAlreadyAbandonedImport(event);
            }
        } catch (UnsupportedTransactionImportRequestedEventVersionException exception) {
            transactionImportMetrics.recordUnsupportedVersion();
            throw exception;
        } catch (TransactionImportProcessingLeaseLostException exception) {
            transactionImportMetrics.recordLostLease();
            transactionImportMetrics.recordFailed();
            throw exception;
        } catch (RuntimeException exception) {
            transactionImportMetrics.recordFailed();
            throw exception;
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private void handleAlreadyCompletedImport(TransactionImportRequestedEvent event) {
        transactionImportMetrics.recordAlreadyCompletedLease();
        transactionImportMetrics.recordDuplicate();

        LOGGER.info(
                "Acknowledging already-completed transaction-import request: eventId={}, importId={}",
                event.getEventId(),
                event.getImportId()
        );
    }

    private void handleAlreadyAbandonedImport(TransactionImportRequestedEvent event) {
        transactionImportMetrics.recordAlreadyAbandonedLease();
        transactionImportMetrics.recordAbandoned();

        LOGGER.info(
                "Acknowledging abandoned transaction-import request: eventId={}, importId={}",
                event.getEventId(),
                event.getImportId()
        );
    }

    private void handleActiveLease(TransactionImportRequestedEvent event) {
        transactionImportMetrics.recordActiveLease();

        throw new TransactionImportJobProcessingException(
                "Transaction import is currently owned by another worker: importId=" + event.getImportId()
        );
    }

    private void handleAcquiredImport(TransactionImportRequestedEvent event,
                                      Visibility visibility,
                                      TransactionImportProcessingAttempt processingAttempt) {
        transactionImportMetrics.recordLeaseAcquired();

        try (TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat =
                     messageVisibilityHeartbeat.start(visibility, processingAttempt)) {
            boolean firstCompletion = eventProcessor.process(event, processingAttempt);

            if (firstCompletion) {
                transactionImportMetrics.recordCompleted();
            } else {
                transactionImportMetrics.recordDuplicate();
            }

            LOGGER.info(
                    "Finished transaction-import request: eventId={}, importId={}, firstCompletion={}",
                    event.getEventId(),
                    event.getImportId(),
                    firstCompletion
            );
        }
    }

    private void validateEventVersion(TransactionImportRequestedEvent event) {
        if (event.getEventVersion() != TransactionImportRequestedEvent.CURRENT_VERSION) {
            throw new UnsupportedTransactionImportRequestedEventVersionException(event.getEventVersion());
        }
    }
}