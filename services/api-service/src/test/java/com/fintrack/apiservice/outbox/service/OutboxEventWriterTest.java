package com.fintrack.apiservice.outbox.service;

import com.fintrack.apiservice.outbox.entity.OutboxEvent;
import com.fintrack.apiservice.outbox.entity.OutboxEventStatus;
import com.fintrack.apiservice.outbox.repository.OutboxEventRepository;
import com.fintrack.eventcontracts.TransactionCreatedEvent;
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
    void writeTransactionCreated_createsPendingOutboxEvent() {
        Long transactionId = 41L;
        Long userId = 7L;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "generated-event-id");
        payload.put("eventVersion", 1);
        payload.put("transactionId", transactionId);
        payload.put("userId", userId);
        payload.put("occurredAt", "2026-08-05T15:00:00Z");

        when(jsonMapper.convertValue(
                any(TransactionCreatedEvent.class),
                org.mockito.ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        outboxEventWriter.writeTransactionCreated(transactionId, userId);

        ArgumentCaptor<OutboxEvent> outboxEventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxEventCaptor.capture());

        OutboxEvent savedOutboxEvent = outboxEventCaptor.getValue();

        assertThat(savedOutboxEvent.getEventId()).isNotNull();
        assertThat(savedOutboxEvent.getAggregateType()).isEqualTo("FINANCIAL_TRANSACTION");
        assertThat(savedOutboxEvent.getAggregateId()).isEqualTo(transactionId);
        assertThat(savedOutboxEvent.getEventType()).isEqualTo("TRANSACTION_CREATED");
        assertThat(savedOutboxEvent.getEventVersion()).isEqualTo(TransactionCreatedEvent.CURRENT_VERSION);
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
    void writeTransactionCreated_whenSerializationFails_doesNotSaveOutboxEvent() {
        when(jsonMapper.convertValue(
                any(TransactionCreatedEvent.class),
                org.mockito.ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenThrow(new IllegalArgumentException("Serialization failed"));

        assertThatThrownBy(() -> outboxEventWriter.writeTransactionCreated(41L, 7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Serialization failed");

        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }
}