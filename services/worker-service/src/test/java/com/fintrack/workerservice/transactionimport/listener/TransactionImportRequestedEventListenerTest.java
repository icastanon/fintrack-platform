package com.fintrack.workerservice.transactionimport.listener;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportJobProcessingException;
import com.fintrack.workerservice.transactionimport.exception.UnsupportedTransactionImportRequestedEventVersionException;
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
    private static final String CORRELATION_ID = "import-request-123";

    @Mock
    private TransactionImportRequestedEventProcessor eventProcessor;

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
    void handleStartsHeartbeatProcessesEventAndClosesHeartbeat() {
        TransactionImportRequestedEvent event = currentEvent();

        when(messageVisibilityHeartbeat.start(visibility, EVENT_ID, IMPORT_ID))
                .thenReturn(runningHeartbeat);
        when(eventProcessor.process(event)).thenReturn(true);

        assertThatCode(() -> listener.handle(event, visibility)).doesNotThrowAnyException();

        InOrder order = inOrder(messageVisibilityHeartbeat, eventProcessor, runningHeartbeat);

        order.verify(messageVisibilityHeartbeat).start(visibility, EVENT_ID, IMPORT_ID);
        order.verify(eventProcessor).process(event);
        order.verify(runningHeartbeat).close();

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleAcceptsPreviouslyFinalizedEventAndClosesHeartbeat() {
        TransactionImportRequestedEvent event = currentEvent();

        when(messageVisibilityHeartbeat.start(visibility, EVENT_ID, IMPORT_ID))
                .thenReturn(runningHeartbeat);
        when(eventProcessor.process(event)).thenReturn(false);

        assertThatCode(() -> listener.handle(event, visibility)).doesNotThrowAnyException();

        InOrder order = inOrder(messageVisibilityHeartbeat, eventProcessor, runningHeartbeat);

        order.verify(messageVisibilityHeartbeat).start(visibility, EVENT_ID, IMPORT_ID);
        order.verify(eventProcessor).process(event);
        order.verify(runningHeartbeat).close();

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRejectsUnsupportedEventVersionBeforeStartingHeartbeat() {
        TransactionImportRequestedEvent event = new TransactionImportRequestedEvent(
                EVENT_ID,
                TransactionImportRequestedEvent.CURRENT_VERSION + 1,
                IMPORT_ID,
                15L,
                7L,
                "imports/7/41/source.csv",
                CORRELATION_ID,
                Instant.parse("2026-08-10T20:00:00Z")
        );

        assertThatThrownBy(() -> listener.handle(event, visibility))
                .isInstanceOf(UnsupportedTransactionImportRequestedEventVersionException.class)
                .hasMessage(
                        "Unsupported transaction-import request event version: 2. Supported version: 1"
                );

        verifyNoInteractions(
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

        when(messageVisibilityHeartbeat.start(visibility, EVENT_ID, IMPORT_ID))
                .thenReturn(runningHeartbeat);
        when(eventProcessor.process(event)).thenThrow(cause);

        assertThatThrownBy(() -> listener.handle(event, visibility)).isSameAs(cause);

        InOrder order = inOrder(messageVisibilityHeartbeat, eventProcessor, runningHeartbeat);

        order.verify(messageVisibilityHeartbeat).start(visibility, EVENT_ID, IMPORT_ID);
        order.verify(eventProcessor).process(event);
        order.verify(runningHeartbeat).close();

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handlePropagatesInitialHeartbeatFailureWithoutProcessingEvent() {
        TransactionImportRequestedEvent event = currentEvent();
        RuntimeException cause = new IllegalStateException("SQS visibility update failed");

        when(messageVisibilityHeartbeat.start(visibility, EVENT_ID, IMPORT_ID))
                .thenThrow(cause);

        assertThatThrownBy(() -> listener.handle(event, visibility)).isSameAs(cause);

        verify(messageVisibilityHeartbeat).start(visibility, EVENT_ID, IMPORT_ID);
        verifyNoInteractions(eventProcessor, runningHeartbeat);

        assertThat(MDC.get("correlationId")).isNull();
    }

    private TransactionImportRequestedEvent currentEvent() {
        return TransactionImportRequestedEvent.create(
                EVENT_ID,
                IMPORT_ID,
                15L,
                7L,
                "imports/7/41/source.csv",
                CORRELATION_ID,
                Instant.parse("2026-08-10T20:00:00Z")
        );
    }
}