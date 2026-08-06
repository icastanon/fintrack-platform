package com.fintrack.workerservice.transaction.service;

import com.fintrack.eventcontracts.TransactionCreatedEvent;
import com.fintrack.workerservice.idempotency.service.ProcessedMessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionCreatedEventProcessorTest {

    @Mock
    private ProcessedMessageService processedMessageService;

    @InjectMocks
    private TransactionCreatedEventProcessor transactionCreatedEventProcessor;

    @Test
    void process_whenEventIsNew_returnsTrue() {
        TransactionCreatedEvent event = createEvent();

        when(processedMessageService.recordIfFirst(
                event.getEventId(),
                "transaction-created-processor",
                "TRANSACTION_CREATED",
                event.getEventVersion()
        )).thenReturn(true);

        boolean firstProcessing = transactionCreatedEventProcessor.process(event);

        assertThat(firstProcessing).isTrue();

        verify(processedMessageService).recordIfFirst(
                event.getEventId(),
                "transaction-created-processor",
                "TRANSACTION_CREATED",
                event.getEventVersion()
        );
    }

    @Test
    void process_whenEventIsDuplicate_returnsFalse() {
        TransactionCreatedEvent event = createEvent();

        when(processedMessageService.recordIfFirst(
                event.getEventId(),
                "transaction-created-processor",
                "TRANSACTION_CREATED",
                event.getEventVersion()
        )).thenReturn(false);

        boolean firstProcessing = transactionCreatedEventProcessor.process(event);

        assertThat(firstProcessing).isFalse();

        verify(processedMessageService).recordIfFirst(
                event.getEventId(),
                "transaction-created-processor",
                "TRANSACTION_CREATED",
                event.getEventVersion()
        );
    }

    private TransactionCreatedEvent createEvent() {
        return TransactionCreatedEvent.create(
                UUID.randomUUID(),
                100L,
                25L,
                Instant.parse("2026-08-06T12:00:00Z")
        );
    }
}