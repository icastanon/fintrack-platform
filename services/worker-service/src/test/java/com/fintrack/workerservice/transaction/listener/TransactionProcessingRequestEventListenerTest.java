package com.fintrack.workerservice.transaction.listener;

import com.fintrack.eventcontracts.TransactionProcessingReason;
import com.fintrack.eventcontracts.TransactionProcessingRequestEvent;
import com.fintrack.workerservice.transaction.exception.UnsupportedTransactionProcessingRequestEventVersionException;
import com.fintrack.workerservice.transaction.service.TransactionProcessingRequestEventProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionProcessingRequestEventListenerTest {

    @Mock
    private TransactionProcessingRequestEventProcessor transactionProcessingRequestEventProcessor;

    @InjectMocks
    private TransactionProcessingRequestEventListener listener;

    @Test
    void handleAcceptsCurrentEventVersionAndDelegatesProcessing() {
        TransactionProcessingRequestEvent event = TransactionProcessingRequestEvent.create(
                UUID.randomUUID(),
                100L,
                25L,
                TransactionProcessingReason.CREATED,
                Instant.parse("2026-08-06T12:00:00Z")
        );

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();

        verify(transactionProcessingRequestEventProcessor).process(event);
    }

    @Test
    void handleRejectsUnsupportedEventVersionWithoutProcessing() {
        TransactionProcessingRequestEvent event = new TransactionProcessingRequestEvent(
                UUID.randomUUID(),
                TransactionProcessingRequestEvent.CURRENT_VERSION + 1,
                100L,
                25L,
                TransactionProcessingReason.CREATED,
                Instant.parse("2026-08-06T12:00:00Z")
        );

        assertThatThrownBy(() -> listener.handle(event))
                .isInstanceOf(UnsupportedTransactionProcessingRequestEventVersionException.class)
                .hasMessage(
                        "Unsupported transaction-processing request event version: 2. Supported version: 1"
                );

        verify(transactionProcessingRequestEventProcessor, never()).process(event);
    }
}