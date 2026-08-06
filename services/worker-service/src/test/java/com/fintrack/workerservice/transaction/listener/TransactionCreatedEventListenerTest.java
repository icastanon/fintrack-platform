package com.fintrack.workerservice.transaction.listener;

import com.fintrack.eventcontracts.TransactionCreatedEvent;
import com.fintrack.workerservice.transaction.exception.UnsupportedTransactionCreatedEventVersionException;
import com.fintrack.workerservice.transaction.service.TransactionCreatedEventProcessor;
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
class TransactionCreatedEventListenerTest {

    @Mock
    private TransactionCreatedEventProcessor transactionCreatedEventProcessor;

    @InjectMocks
    private TransactionCreatedEventListener listener;

    @Test
    void handleAcceptsCurrentEventVersionAndDelegatesProcessing() {
        TransactionCreatedEvent event = TransactionCreatedEvent.create(
                UUID.randomUUID(),
                100L,
                25L,
                Instant.parse("2026-08-06T12:00:00Z")
        );

        assertThatCode(() -> listener.handle(event))
                .doesNotThrowAnyException();

        verify(transactionCreatedEventProcessor).process(event);
    }

    @Test
    void handleRejectsUnsupportedEventVersionWithoutProcessing() {
        TransactionCreatedEvent event = new TransactionCreatedEvent(
                UUID.randomUUID(),
                TransactionCreatedEvent.CURRENT_VERSION + 1,
                100L,
                25L,
                Instant.parse("2026-08-06T12:00:00Z")
        );

        assertThatThrownBy(() -> listener.handle(event))
                .isInstanceOf(
                        UnsupportedTransactionCreatedEventVersionException.class
                )
                .hasMessage(
                        "Unsupported transaction-created event version: 2. Supported version: 1"
                );

        verify(transactionCreatedEventProcessor, never()).process(event);
    }
}