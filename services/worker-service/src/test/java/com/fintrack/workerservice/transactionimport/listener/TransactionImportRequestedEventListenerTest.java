package com.fintrack.workerservice.transactionimport.listener;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportJobProcessingException;
import com.fintrack.workerservice.transactionimport.exception.UnsupportedTransactionImportRequestedEventVersionException;
import com.fintrack.workerservice.transactionimport.service.TransactionImportRequestedEventProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportRequestedEventListenerTest {

    private static final String CORRELATION_ID = "import-request-123";

    @Mock
    private TransactionImportRequestedEventProcessor eventProcessor;

    @InjectMocks
    private TransactionImportRequestedEventListener listener;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void handleProcessesCurrentEventVersionAndClearsCorrelationId() {
        TransactionImportRequestedEvent event = currentEvent();

        when(eventProcessor.process(event)).thenReturn(true);

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();

        verify(eventProcessor).process(event);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleAcceptsPreviouslyFinalizedEventAndClearsCorrelationId() {
        TransactionImportRequestedEvent event = currentEvent();

        when(eventProcessor.process(event)).thenReturn(false);

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();

        verify(eventProcessor).process(event);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRejectsUnsupportedEventVersionBeforeProcessing() {
        TransactionImportRequestedEvent event = new TransactionImportRequestedEvent(
                UUID.randomUUID(),
                TransactionImportRequestedEvent.CURRENT_VERSION + 1,
                41L,
                15L,
                7L,
                "imports/7/41/source.csv",
                CORRELATION_ID,
                Instant.parse("2026-08-10T20:00:00Z")
        );

        assertThatThrownBy(() -> listener.handle(event))
                .isInstanceOf(UnsupportedTransactionImportRequestedEventVersionException.class)
                .hasMessage(
                        "Unsupported transaction-import request event version: 2. Supported version: 1"
                );

        verifyNoInteractions(eventProcessor);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handlePropagatesProcessingFailureAndClearsCorrelationId() {
        TransactionImportRequestedEvent event = currentEvent();
        TransactionImportJobProcessingException cause =
                new TransactionImportJobProcessingException("Transaction import job failed");

        when(eventProcessor.process(event)).thenThrow(cause);

        assertThatThrownBy(() -> listener.handle(event)).isSameAs(cause);

        verify(eventProcessor).process(event);
        assertThat(MDC.get("correlationId")).isNull();
    }

    private TransactionImportRequestedEvent currentEvent() {
        return TransactionImportRequestedEvent.create(
                UUID.randomUUID(),
                41L,
                15L,
                7L,
                "imports/7/41/source.csv",
                CORRELATION_ID,
                Instant.parse("2026-08-10T20:00:00Z")
        );
    }
}