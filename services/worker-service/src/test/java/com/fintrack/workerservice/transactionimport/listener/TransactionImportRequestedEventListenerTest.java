package com.fintrack.workerservice.transactionimport.listener;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportJobProcessingException;
import com.fintrack.workerservice.transactionimport.exception.UnsupportedTransactionImportRequestedEventVersionException;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingLeaseAcquisition;
import com.fintrack.workerservice.transactionimport.service.TransactionImportProcessingLeaseManager;
import com.fintrack.workerservice.transactionimport.service.TransactionImportRequestedEventProcessor;
import io.awspring.cloud.sqs.listener.Visibility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportRequestedEventListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("8fb4e595-dbbc-4b7f-a791-8902bf5d93e1");
    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 15L;
    private static final Long USER_ID = 7L;
    private static final String CORRELATION_ID = "import-request-123";

    private static final TransactionImportProcessingAttempt PROCESSING_ATTEMPT =
            new TransactionImportProcessingAttempt(
                    EVENT_ID,
                    IMPORT_ID,
                    ACCOUNT_ID,
                    USER_ID,
                    "worker-a",
                    3L
            );

    @Mock
    private TransactionImportRequestedEventProcessor eventProcessor;

    @Mock
    private TransactionImportProcessingLeaseManager processingLeaseManager;

    @Mock
    private TransactionImportMessageVisibilityHeartbeat messageVisibilityHeartbeat;

    @Mock
    private TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat;

    @Mock
    private Visibility visibility;

    @InjectMocks
    private TransactionImportRequestedEventListener listener;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void handleAcquiresLeaseStartsHeartbeatAndProcessesEvent() {
        TransactionImportRequestedEvent event = currentEvent();

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.acquired(PROCESSING_ATTEMPT));
        when(messageVisibilityHeartbeat.start(visibility, PROCESSING_ATTEMPT)).thenReturn(runningHeartbeat);
        when(eventProcessor.process(event)).thenReturn(true);
        when(runningHeartbeat.hasLostProcessingLease()).thenReturn(false);

        assertThatCode(() -> listener.handle(event, visibility)).doesNotThrowAnyException();

        InOrder order = inOrder(
                processingLeaseManager,
                messageVisibilityHeartbeat,
                eventProcessor,
                runningHeartbeat
        );

        order.verify(processingLeaseManager).acquire(event);
        order.verify(messageVisibilityHeartbeat).start(visibility, PROCESSING_ATTEMPT);
        order.verify(eventProcessor).process(event);
        order.verify(runningHeartbeat).hasLostProcessingLease();
        order.verify(runningHeartbeat).close();

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleAcceptsPreviouslyFinalizedBatchExecution() {
        TransactionImportRequestedEvent event = currentEvent();

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.acquired(PROCESSING_ATTEMPT));
        when(messageVisibilityHeartbeat.start(visibility, PROCESSING_ATTEMPT)).thenReturn(runningHeartbeat);
        when(eventProcessor.process(event)).thenReturn(false);
        when(runningHeartbeat.hasLostProcessingLease()).thenReturn(false);

        assertThatCode(() -> listener.handle(event, visibility)).doesNotThrowAnyException();

        verify(eventProcessor).process(event);
        verify(runningHeartbeat).close();

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleAcknowledgesImportThatIsAlreadyCompleted() {
        TransactionImportRequestedEvent event = currentEvent();

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.alreadyCompleted());

        assertThatCode(() -> listener.handle(event, visibility)).doesNotThrowAnyException();

        verify(processingLeaseManager).acquire(event);
        verifyNoInteractions(eventProcessor, messageVisibilityHeartbeat, runningHeartbeat, visibility);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRejectsImportOwnedByAnotherWorker() {
        TransactionImportRequestedEvent event = currentEvent();

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.activeLease());

        assertThatThrownBy(() -> listener.handle(event, visibility))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage("Transaction import is currently owned by another worker: importId=" + IMPORT_ID);

        verify(processingLeaseManager).acquire(event);
        verifyNoInteractions(eventProcessor, messageVisibilityHeartbeat, runningHeartbeat, visibility);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRejectsUnsupportedVersionBeforeAcquiringLease() {
        TransactionImportRequestedEvent event = new TransactionImportRequestedEvent(
                EVENT_ID,
                TransactionImportRequestedEvent.CURRENT_VERSION + 1,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                "imports/7/41/source.csv",
                CORRELATION_ID,
                Instant.parse("2026-08-10T20:00:00Z")
        );

        assertThatThrownBy(() -> listener.handle(event, visibility))
                .isInstanceOf(UnsupportedTransactionImportRequestedEventVersionException.class)
                .hasMessage("Unsupported transaction-import request event version: 2. Supported version: 1");

        verifyNoInteractions(
                processingLeaseManager,
                eventProcessor,
                messageVisibilityHeartbeat,
                runningHeartbeat,
                visibility
        );

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handlePropagatesProcessingFailureAndClosesHeartbeat() {
        TransactionImportRequestedEvent event = currentEvent();
        TransactionImportJobProcessingException cause =
                new TransactionImportJobProcessingException("Transaction import job failed");

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.acquired(PROCESSING_ATTEMPT));
        when(messageVisibilityHeartbeat.start(visibility, PROCESSING_ATTEMPT)).thenReturn(runningHeartbeat);
        when(eventProcessor.process(event)).thenThrow(cause);

        assertThatThrownBy(() -> listener.handle(event, visibility)).isSameAs(cause);

        InOrder order = inOrder(
                processingLeaseManager,
                messageVisibilityHeartbeat,
                eventProcessor,
                runningHeartbeat
        );

        order.verify(processingLeaseManager).acquire(event);
        order.verify(messageVisibilityHeartbeat).start(visibility, PROCESSING_ATTEMPT);
        order.verify(eventProcessor).process(event);
        order.verify(runningHeartbeat).close();

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handlePropagatesInitialHeartbeatFailureWithoutProcessingEvent() {
        TransactionImportRequestedEvent event = currentEvent();
        RuntimeException cause = new IllegalStateException("SQS visibility update failed");

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.acquired(PROCESSING_ATTEMPT));
        when(messageVisibilityHeartbeat.start(visibility, PROCESSING_ATTEMPT)).thenThrow(cause);

        assertThatThrownBy(() -> listener.handle(event, visibility)).isSameAs(cause);

        verify(processingLeaseManager).acquire(event);
        verify(messageVisibilityHeartbeat).start(visibility, PROCESSING_ATTEMPT);
        verifyNoInteractions(eventProcessor, runningHeartbeat);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRejectsSuccessfulProcessingWhenLeaseWasLost() {
        TransactionImportRequestedEvent event = currentEvent();

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.acquired(PROCESSING_ATTEMPT));
        when(messageVisibilityHeartbeat.start(visibility, PROCESSING_ATTEMPT)).thenReturn(runningHeartbeat);
        when(eventProcessor.process(event)).thenReturn(true);
        when(runningHeartbeat.hasLostProcessingLease()).thenReturn(true);

        assertThatThrownBy(() -> listener.handle(event, visibility))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage("Transaction import processing lease was lost: importId=" + IMPORT_ID);

        InOrder order = inOrder(eventProcessor, runningHeartbeat);

        order.verify(eventProcessor).process(event);
        order.verify(runningHeartbeat).hasLostProcessingLease();
        order.verify(runningHeartbeat).close();

        assertThat(MDC.get("correlationId")).isNull();
    }

    private TransactionImportRequestedEvent currentEvent() {
        return TransactionImportRequestedEvent.create(
                EVENT_ID,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                "imports/7/41/source.csv",
                CORRELATION_ID,
                Instant.parse("2026-08-10T20:00:00Z")
        );
    }
}