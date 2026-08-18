package com.fintrack.workerservice.transaction.listener;

import com.fintrack.eventcontracts.TransactionProcessingRequestEvent;
import com.fintrack.workerservice.transaction.exception.UnsupportedTransactionProcessingRequestEventVersionException;
import com.fintrack.workerservice.transaction.metrics.TransactionProcessingMetrics;
import com.fintrack.workerservice.transaction.service.TransactionProcessingRequestEventProcessor;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class TransactionProcessingRequestEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionProcessingRequestEventListener.class);
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private final TransactionProcessingRequestEventProcessor transactionProcessingRequestEventProcessor;
    private final TransactionProcessingMetrics transactionProcessingMetrics;

    public TransactionProcessingRequestEventListener(
            TransactionProcessingRequestEventProcessor transactionProcessingRequestEventProcessor,
            TransactionProcessingMetrics transactionProcessingMetrics) {
        this.transactionProcessingRequestEventProcessor = transactionProcessingRequestEventProcessor;
        this.transactionProcessingMetrics = transactionProcessingMetrics;
    }

    @SqsListener("${fintrack.sqs.transaction-processing-queue}")
    public void handle(TransactionProcessingRequestEvent event) {
        MDC.put(CORRELATION_ID_MDC_KEY, event.getCorrelationId());

        try {
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

            boolean firstProcessing = transactionProcessingRequestEventProcessor.process(event);

            if (firstProcessing) {
                transactionProcessingMetrics.recordProcessed();
            } else {
                transactionProcessingMetrics.recordDuplicate();
            }
        } catch (UnsupportedTransactionProcessingRequestEventVersionException exception) {
            transactionProcessingMetrics.recordUnsupportedVersion();
            throw exception;
        } catch (RuntimeException exception) {
            transactionProcessingMetrics.recordFailed();
            throw exception;
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private void validateEventVersion(TransactionProcessingRequestEvent event) {
        if (event.getEventVersion() != TransactionProcessingRequestEvent.CURRENT_VERSION) {
            throw new UnsupportedTransactionProcessingRequestEventVersionException(event.getEventVersion());
        }
    }
}