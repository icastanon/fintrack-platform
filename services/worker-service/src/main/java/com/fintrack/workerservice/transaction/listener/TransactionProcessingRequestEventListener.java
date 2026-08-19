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

import java.sql.SQLException;

@Component
public class TransactionProcessingRequestEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionProcessingRequestEventListener.class);
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";
    private static final String POSTGRESQL_LOCK_TIMEOUT_SQL_STATE = "55P03";

    private final TransactionProcessingRequestEventProcessor transactionProcessingRequestEventProcessor;
    private final TransactionProcessingMetrics transactionProcessingMetrics;

    public TransactionProcessingRequestEventListener(
            TransactionProcessingRequestEventProcessor transactionProcessingRequestEventProcessor,
            TransactionProcessingMetrics transactionProcessingMetrics) {
        this.transactionProcessingRequestEventProcessor = transactionProcessingRequestEventProcessor;
        this.transactionProcessingMetrics = transactionProcessingMetrics;
    }

    @SqsListener(
            value = "${fintrack.sqs.transaction-processing-queue}",
            maxConcurrentMessages = "${fintrack.sqs.transaction-processing-max-concurrent-messages}",
            maxMessagesPerPoll = "${fintrack.sqs.transaction-processing-max-messages-per-poll}"
    )
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
            recordFailureOutcome(exception);
            throw exception;
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    private void recordFailureOutcome(RuntimeException exception) {
        if (isPostgreSqlLockTimeout(exception)) {
            transactionProcessingMetrics.recordLockTimeout();
        } else {
            transactionProcessingMetrics.recordFailed();
        }
    }

    private boolean isPostgreSqlLockTimeout(Throwable exception) {
        //checking exception instead of catching because wrapper can vary depending on where it is produced.
        Throwable current = exception;

        while (current != null) {
            if (current instanceof SQLException sqlException
                    && POSTGRESQL_LOCK_TIMEOUT_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private void validateEventVersion(TransactionProcessingRequestEvent event) {
        if (event.getEventVersion() != TransactionProcessingRequestEvent.CURRENT_VERSION) {
            throw new UnsupportedTransactionProcessingRequestEventVersionException(event.getEventVersion());
        }
    }
}