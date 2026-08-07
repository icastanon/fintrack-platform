package com.fintrack.workerservice.transaction.service;

import com.fintrack.eventcontracts.TransactionCreatedEvent;
import com.fintrack.workerservice.idempotency.service.ProcessedMessageService;
import com.fintrack.workerservice.transaction.entity.FinancialTransaction;
import com.fintrack.workerservice.transaction.exception.FinancialTransactionNotFoundException;
import com.fintrack.workerservice.transaction.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionCreatedEventProcessorTest {

    @Mock
    private ProcessedMessageService processedMessageService;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private FinancialTransaction financialTransaction;

    @InjectMocks
    private TransactionCreatedEventProcessor transactionCreatedEventProcessor;

    @Test
    void process_whenEventIsNew_loadsOwnedTransactionAndReturnsTrue() {
        TransactionCreatedEvent event = createEvent();

        when(processedMessageService.recordIfFirst(
                event.getEventId(),
                "transaction-created-processor",
                "TRANSACTION_CREATED",
                event.getEventVersion()
        )).thenReturn(true);

        when(financialTransactionRepository.findByIdAndUserId(
                event.getTransactionId(),
                event.getUserId()
        )).thenReturn(Optional.of(financialTransaction));

        boolean firstProcessing =
                transactionCreatedEventProcessor.process(event);

        assertThat(firstProcessing).isTrue();

        verify(financialTransactionRepository).findByIdAndUserId(
                event.getTransactionId(),
                event.getUserId()
        );
    }

    @Test
    void process_whenEventIsDuplicate_doesNotLoadTransaction() {
        TransactionCreatedEvent event = createEvent();

        when(processedMessageService.recordIfFirst(
                event.getEventId(),
                "transaction-created-processor",
                "TRANSACTION_CREATED",
                event.getEventVersion()
        )).thenReturn(false);

        boolean firstProcessing =
                transactionCreatedEventProcessor.process(event);

        assertThat(firstProcessing).isFalse();

        verifyNoInteractions(financialTransactionRepository);
    }

    @Test
    void process_whenTransactionDoesNotBelongToUser_throws() {
        TransactionCreatedEvent event = createEvent();

        when(processedMessageService.recordIfFirst(
                event.getEventId(),
                "transaction-created-processor",
                "TRANSACTION_CREATED",
                event.getEventVersion()
        )).thenReturn(true);

        when(financialTransactionRepository.findByIdAndUserId(
                event.getTransactionId(),
                event.getUserId()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                transactionCreatedEventProcessor.process(event)
        )
                .isInstanceOf(FinancialTransactionNotFoundException.class)
                .hasMessage(
                        "Financial transaction 100 was not found for user 25"
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