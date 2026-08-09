package com.fintrack.apiservice.outbox.service;

import com.fintrack.apiservice.outbox.entity.OutboxEvent;
import com.fintrack.apiservice.outbox.entity.OutboxEventStatus;
import com.fintrack.apiservice.outbox.repository.OutboxEventRepository;
import com.fintrack.eventcontracts.TransactionProcessingRequestEvent;
import com.fintrack.eventcontracts.TransactionProcessingReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventWriterTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private JsonMapper jsonMapper;

    @InjectMocks
    private OutboxEventWriter outboxEventWriter;

    @Test
    void writeTransactionProcessingRequestedCreatesPendingOutboxEvent() {
        Long transactionId = 41L;
        Long userId = 7L;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "generated-event-id");
        payload.put("eventVersion", 1);
        payload.put("transactionId", transactionId);
        payload.put("userId", userId);
        payload.put("reason", "CREATED");
        payload.put("occurredAt", "2026-08-05T15:00:00Z");

        when(jsonMapper.convertValue(
                any(TransactionProcessingRequestEvent.class),
                org.mockito.ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        outboxEventWriter.writeTransactionProcessingRequested(
                transactionId,
                userId,
                TransactionProcessingReason.CREATED
        );

        ArgumentCaptor<TransactionProcessingRequestEvent> eventCaptor =
                ArgumentCaptor.forClass(TransactionProcessingRequestEvent.class);

        verify(jsonMapper).convertValue(
                eventCaptor.capture(),
                org.mockito.ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        );

        TransactionProcessingRequestEvent capturedEvent = eventCaptor.getValue();

        assertThat(capturedEvent.getEventId()).isNotNull();
        assertThat(capturedEvent.getTransactionId()).isEqualTo(transactionId);
        assertThat(capturedEvent.getUserId()).isEqualTo(userId);
        assertThat(capturedEvent.getReason()).isEqualTo(TransactionProcessingReason.CREATED);
        assertThat(capturedEvent.getOccurredAt()).isNotNull();

        ArgumentCaptor<OutboxEvent> outboxEventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxEventCaptor.capture());

        OutboxEvent savedOutboxEvent = outboxEventCaptor.getValue();

        assertThat(savedOutboxEvent.getEventId()).isEqualTo(capturedEvent.getEventId());
        assertThat(savedOutboxEvent.getAggregateType()).isEqualTo("FINANCIAL_TRANSACTION");
        assertThat(savedOutboxEvent.getAggregateId()).isEqualTo(transactionId);
        assertThat(savedOutboxEvent.getEventType()).isEqualTo("TRANSACTION_PROCESSING_REQUESTED");
        assertThat(savedOutboxEvent.getEventVersion()).isEqualTo(TransactionProcessingRequestEvent.CURRENT_VERSION);
        assertThat(savedOutboxEvent.getPayload()).isEqualTo(payload);
        assertThat(savedOutboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedOutboxEvent.getAttemptCount()).isZero();
        assertThat(savedOutboxEvent.getAvailableAt()).isNotNull();
        assertThat(savedOutboxEvent.getLockedAt()).isNull();
        assertThat(savedOutboxEvent.getLockOwner()).isNull();
        assertThat(savedOutboxEvent.getPublishedAt()).isNull();
        assertThat(savedOutboxEvent.getLastError()).isNull();
    }

    @Test
    void writeTransactionProcessingRequestedWhenSerializationFailsDoesNotSaveOutboxEvent() {
        when(jsonMapper.convertValue(
                any(TransactionProcessingRequestEvent.class),
                org.mockito.ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenThrow(new IllegalArgumentException("Serialization failed"));

        assertThatThrownBy(() ->
                outboxEventWriter.writeTransactionProcessingRequested(
                        41L,
                        7L,
                        TransactionProcessingReason.CREATED
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Serialization failed");

        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }
}