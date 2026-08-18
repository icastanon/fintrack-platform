package com.fintrack.workerservice.transaction.listener;

import com.fintrack.eventcontracts.TransactionProcessingReason;
import com.fintrack.eventcontracts.TransactionProcessingRequestEvent;
import com.fintrack.workerservice.transaction.exception.UnsupportedTransactionProcessingRequestEventVersionException;
import com.fintrack.workerservice.transaction.metrics.TransactionProcessingMetrics;
import com.fintrack.workerservice.transaction.service.TransactionProcessingRequestEventProcessor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionProcessingRequestEventListenerTest {

    private static final String CORRELATION_ID = "request-123";

    @Mock
    private TransactionProcessingRequestEventProcessor transactionProcessingRequestEventProcessor;

    @Mock
    private TransactionProcessingMetrics transactionProcessingMetrics;

    @InjectMocks
    private TransactionProcessingRequestEventListener listener;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void handleRecordsProcessedOutcomeForFirstProcessing() {
        TransactionProcessingRequestEvent event = createCurrentEvent();

        when(transactionProcessingRequestEventProcessor.process(event)).thenAnswer(invocation -> {
            assertThat(MDC.get("correlationId")).isEqualTo(CORRELATION_ID);
            return true;
        });

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();

        verify(transactionProcessingRequestEventProcessor).process(event);
        verify(transactionProcessingMetrics).recordProcessed();
        verify(transactionProcessingMetrics, never()).recordDuplicate();
        verify(transactionProcessingMetrics, never()).recordFailed();
        verify(transactionProcessingMetrics, never()).recordUnsupportedVersion();

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRecordsDuplicateOutcomeWhenMessageWasAlreadyProcessed() {
        TransactionProcessingRequestEvent event = createCurrentEvent();

        when(transactionProcessingRequestEventProcessor.process(event)).thenReturn(false);

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();

        verify(transactionProcessingMetrics).recordDuplicate();
        verify(transactionProcessingMetrics, never()).recordProcessed();
        verify(transactionProcessingMetrics, never()).recordFailed();
        verify(transactionProcessingMetrics, never()).recordUnsupportedVersion();

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRejectsUnsupportedVersionAndRecordsOutcome() {
        TransactionProcessingRequestEvent event = new TransactionProcessingRequestEvent(
                UUID.randomUUID(),
                TransactionProcessingRequestEvent.CURRENT_VERSION + 1,
                100L,
                25L,
                TransactionProcessingReason.CREATED,
                CORRELATION_ID,
                Instant.parse("2026-08-06T12:00:00Z")
        );

        assertThatThrownBy(() -> listener.handle(event))
                .isInstanceOf(UnsupportedTransactionProcessingRequestEventVersionException.class)
                .hasMessage("Unsupported transaction-processing request event version: 2. Supported version: 1");

        verify(transactionProcessingRequestEventProcessor, never()).process(event);
        verify(transactionProcessingMetrics).recordUnsupportedVersion();
        verify(transactionProcessingMetrics, never()).recordProcessed();
        verify(transactionProcessingMetrics, never()).recordDuplicate();
        verify(transactionProcessingMetrics, never()).recordFailed();

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRecordsFailureAndRemovesCorrelationIdWhenProcessingFails() {
        TransactionProcessingRequestEvent event = createCurrentEvent();

        when(transactionProcessingRequestEventProcessor.process(event))
                .thenThrow(new IllegalStateException("Processing failed"));

        assertThatThrownBy(() -> listener.handle(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Processing failed");

        verify(transactionProcessingMetrics).recordFailed();
        verify(transactionProcessingMetrics, never()).recordProcessed();
        verify(transactionProcessingMetrics, never()).recordDuplicate();
        verify(transactionProcessingMetrics, never()).recordUnsupportedVersion();

        assertThat(MDC.get("correlationId")).isNull();
    }

    private TransactionProcessingRequestEvent createCurrentEvent() {
        return TransactionProcessingRequestEvent.create(
                UUID.randomUUID(),
                100L,
                25L,
                TransactionProcessingReason.CREATED,
                CORRELATION_ID,
                Instant.parse("2026-08-06T12:00:00Z")
        );
    }
}