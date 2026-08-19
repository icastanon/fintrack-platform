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
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
    private TransactionImportMetrics transactionImportMetrics;

    @Mock
    private Visibility visibility;

    @InjectMocks
    private TransactionImportRequestedEventListener listener;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void handleRecordsCompletedWhenImportIsFinalizedForFirstTime() {
        TransactionImportRequestedEvent event = currentEvent();

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.acquired(PROCESSING_ATTEMPT));
        when(messageVisibilityHeartbeat.start(visibility, PROCESSING_ATTEMPT)).thenReturn(runningHeartbeat);
        when(eventProcessor.process(event, PROCESSING_ATTEMPT)).thenReturn(true);

        assertThatCode(() -> listener.handle(event, visibility)).doesNotThrowAnyException();

        InOrder order = inOrder(
                processingLeaseManager,
                messageVisibilityHeartbeat,
                eventProcessor,
                runningHeartbeat
        );

        order.verify(processingLeaseManager).acquire(event);
        order.verify(messageVisibilityHeartbeat).start(visibility, PROCESSING_ATTEMPT);
        order.verify(eventProcessor).process(event, PROCESSING_ATTEMPT);
        order.verify(runningHeartbeat).close();

        verify(transactionImportMetrics).recordLeaseAcquired();
        verify(transactionImportMetrics).recordCompleted();
        verifyNoMoreInteractions(transactionImportMetrics);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRecordsDuplicateWhenAcquiredImportWasPreviouslyFinalized() {
        TransactionImportRequestedEvent event = currentEvent();

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.acquired(PROCESSING_ATTEMPT));
        when(messageVisibilityHeartbeat.start(visibility, PROCESSING_ATTEMPT)).thenReturn(runningHeartbeat);
        when(eventProcessor.process(event, PROCESSING_ATTEMPT)).thenReturn(false);

        assertThatCode(() -> listener.handle(event, visibility)).doesNotThrowAnyException();

        verify(eventProcessor).process(event, PROCESSING_ATTEMPT);
        verify(runningHeartbeat).close();
        verify(transactionImportMetrics).recordLeaseAcquired();
        verify(transactionImportMetrics).recordDuplicate();
        verifyNoMoreInteractions(transactionImportMetrics);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleAcknowledgesAndRecordsDuplicateWhenImportIsAlreadyCompleted() {
        TransactionImportRequestedEvent event = currentEvent();

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.alreadyCompleted());

        assertThatCode(() -> listener.handle(event, visibility)).doesNotThrowAnyException();

        verify(processingLeaseManager).acquire(event);
        verify(transactionImportMetrics).recordAlreadyCompletedLease();
        verify(transactionImportMetrics).recordDuplicate();
        verifyNoMoreInteractions(transactionImportMetrics);
        verifyNoInteractions(eventProcessor, messageVisibilityHeartbeat, runningHeartbeat, visibility);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRecordsActiveLeaseAndFailedWhenAnotherWorkerOwnsImport() {
        TransactionImportRequestedEvent event = currentEvent();

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.activeLease());

        assertThatThrownBy(() -> listener.handle(event, visibility))
                .isInstanceOf(TransactionImportJobProcessingException.class)
                .hasMessage("Transaction import is currently owned by another worker: importId=" + IMPORT_ID);

        verify(processingLeaseManager).acquire(event);
        verify(transactionImportMetrics).recordActiveLease();
        verify(transactionImportMetrics).recordFailed();
        verifyNoMoreInteractions(transactionImportMetrics);
        verifyNoInteractions(eventProcessor, messageVisibilityHeartbeat, runningHeartbeat, visibility);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRecordsUnsupportedVersionBeforeAcquiringLease() {
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

        verify(transactionImportMetrics).recordUnsupportedVersion();
        verifyNoMoreInteractions(transactionImportMetrics);
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
    void handleRecordsFailedWhenProcessingFails() {
        TransactionImportRequestedEvent event = currentEvent();
        TransactionImportJobProcessingException cause =
                new TransactionImportJobProcessingException("Transaction import job failed");

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.acquired(PROCESSING_ATTEMPT));
        when(messageVisibilityHeartbeat.start(visibility, PROCESSING_ATTEMPT)).thenReturn(runningHeartbeat);
        when(eventProcessor.process(event, PROCESSING_ATTEMPT)).thenThrow(cause);

        assertThatThrownBy(() -> listener.handle(event, visibility)).isSameAs(cause);

        InOrder order = inOrder(
                processingLeaseManager,
                messageVisibilityHeartbeat,
                eventProcessor,
                runningHeartbeat
        );

        order.verify(processingLeaseManager).acquire(event);
        order.verify(messageVisibilityHeartbeat).start(visibility, PROCESSING_ATTEMPT);
        order.verify(eventProcessor).process(event, PROCESSING_ATTEMPT);
        order.verify(runningHeartbeat).close();

        verify(transactionImportMetrics).recordLeaseAcquired();
        verify(transactionImportMetrics).recordFailed();
        verifyNoMoreInteractions(transactionImportMetrics);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRecordsFailedWhenInitialHeartbeatCannotStart() {
        TransactionImportRequestedEvent event = currentEvent();
        RuntimeException cause = new IllegalStateException("SQS visibility update failed");

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.acquired(PROCESSING_ATTEMPT));
        when(messageVisibilityHeartbeat.start(visibility, PROCESSING_ATTEMPT)).thenThrow(cause);

        assertThatThrownBy(() -> listener.handle(event, visibility)).isSameAs(cause);

        verify(processingLeaseManager).acquire(event);
        verify(messageVisibilityHeartbeat).start(visibility, PROCESSING_ATTEMPT);
        verify(transactionImportMetrics).recordLeaseAcquired();
        verify(transactionImportMetrics).recordFailed();
        verifyNoMoreInteractions(transactionImportMetrics);
        verifyNoInteractions(eventProcessor, runningHeartbeat);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRecordsLostLeaseAndFailedWhenProcessingFenceRejectsOwner() {
        TransactionImportRequestedEvent event = currentEvent();
        TransactionImportProcessingLeaseLostException cause =
                new TransactionImportProcessingLeaseLostException(
                        IMPORT_ID,
                        PROCESSING_ATTEMPT.getProcessingOwner(),
                        PROCESSING_ATTEMPT.getFencingToken()
                );

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.acquired(PROCESSING_ATTEMPT));
        when(messageVisibilityHeartbeat.start(visibility, PROCESSING_ATTEMPT)).thenReturn(runningHeartbeat);
        when(eventProcessor.process(event, PROCESSING_ATTEMPT)).thenThrow(cause);

        assertThatThrownBy(() -> listener.handle(event, visibility)).isSameAs(cause);

        verify(eventProcessor).process(event, PROCESSING_ATTEMPT);
        verify(runningHeartbeat).close();
        verify(transactionImportMetrics).recordLeaseAcquired();
        verify(transactionImportMetrics).recordLostLease();
        verify(transactionImportMetrics).recordFailed();
        verifyNoMoreInteractions(transactionImportMetrics);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRecordsFailedWhenLeaseAcquisitionThrows() {
        TransactionImportRequestedEvent event = currentEvent();
        RuntimeException cause = new IllegalStateException("PostgreSQL unavailable");

        when(processingLeaseManager.acquire(event)).thenThrow(cause);

        assertThatThrownBy(() -> listener.handle(event, visibility)).isSameAs(cause);

        verify(processingLeaseManager).acquire(event);
        verify(transactionImportMetrics).recordFailed();
        verifyNoMoreInteractions(transactionImportMetrics);
        verifyNoInteractions(eventProcessor, messageVisibilityHeartbeat, runningHeartbeat, visibility);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleAcknowledgesAbandonedImportWithoutStartingProcessing() {
        TransactionImportRequestedEvent event = currentEvent();

        when(processingLeaseManager.acquire(event))
                .thenReturn(TransactionImportProcessingLeaseAcquisition.alreadyAbandoned());

        assertThatCode(() -> listener.handle(event, visibility)).doesNotThrowAnyException();

        verify(processingLeaseManager).acquire(event);
        verify(transactionImportMetrics).recordAlreadyAbandonedLease();
        verify(transactionImportMetrics).recordAbandoned();
        verifyNoMoreInteractions(transactionImportMetrics);
        verifyNoInteractions(eventProcessor, messageVisibilityHeartbeat, runningHeartbeat, visibility);

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