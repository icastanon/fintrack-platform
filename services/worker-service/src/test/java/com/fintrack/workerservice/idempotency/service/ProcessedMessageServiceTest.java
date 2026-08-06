package com.fintrack.workerservice.idempotency.service;

import com.fintrack.workerservice.idempotency.repository.ProcessedMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessedMessageServiceTest {

    @Mock
    private ProcessedMessageRepository processedMessageRepository;

    @InjectMocks
    private ProcessedMessageService processedMessageService;

    @Test
    void recordIfFirst_whenMarkerIsInserted_returnsTrue() {
        UUID eventId = UUID.randomUUID();

        when(processedMessageRepository.insertIfAbsent(
                eventId,
                "transaction-created-processor",
                "TRANSACTION_CREATED",
                1
        )).thenReturn(1);

        boolean firstProcessing = processedMessageService.recordIfFirst(
                eventId,
                "transaction-created-processor",
                "TRANSACTION_CREATED",
                1
        );

        assertThat(firstProcessing).isTrue();

        verify(processedMessageRepository).insertIfAbsent(
                eventId,
                "transaction-created-processor",
                "TRANSACTION_CREATED",
                1
        );
    }

    @Test
    void recordIfFirst_whenMarkerAlreadyExists_returnsFalse() {
        UUID eventId = UUID.randomUUID();

        when(processedMessageRepository.insertIfAbsent(
                eventId,
                "transaction-created-processor",
                "TRANSACTION_CREATED",
                1
        )).thenReturn(0);

        boolean firstProcessing = processedMessageService.recordIfFirst(
                eventId,
                "transaction-created-processor",
                "TRANSACTION_CREATED",
                1
        );

        assertThat(firstProcessing).isFalse();

        verify(processedMessageRepository).insertIfAbsent(
                eventId,
                "transaction-created-processor",
                "TRANSACTION_CREATED",
                1
        );
    }
}