package com.fintrack.apiservice.outbox.service;

import com.fintrack.apiservice.outbox.entity.OutboxEvent;
import com.fintrack.apiservice.outbox.entity.OutboxEventStatus;
import com.fintrack.apiservice.outbox.repository.OutboxEventRepository;
import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.eventcontracts.TransactionProcessingReason;
import com.fintrack.eventcontracts.TransactionProcessingRequestEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
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

    private static final String CORRELATION_ID = "request-123";

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private JsonMapper jsonMapper;

    @InjectMocks
    private OutboxEventWriter outboxEventWriter;

    @BeforeEach
    void setUpCorrelationId() {
        MDC.put("correlationId", CORRELATION_ID);
    }

    @AfterEach
    void clearCorrelationId() {
        MDC.clear();
    }

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
        payload.put("correlationId", CORRELATION_ID);
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
        assertThat(capturedEvent.getCorrelationId()).isEqualTo(CORRELATION_ID);
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
    void writeTransactionImportRequestedCreatesPendingOutboxEvent() {
        Long importId = 51L;
        Long accountId = 12L;
        Long userId = 7L;
        String sourceObjectKey = "imports/7/import-123/source.csv";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "generated-event-id");
        payload.put("eventVersion", 1);
        payload.put("importId", importId);
        payload.put("accountId", accountId);
        payload.put("userId", userId);
        payload.put("sourceObjectKey", sourceObjectKey);
        payload.put("correlationId", CORRELATION_ID);
        payload.put("occurredAt", "2026-08-10T15:00:00Z");

        when(jsonMapper.convertValue(
                any(TransactionImportRequestedEvent.class),
                org.mockito.ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenReturn(payload);

        outboxEventWriter.writeTransactionImportRequested(importId, accountId, userId, sourceObjectKey);

        ArgumentCaptor<TransactionImportRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(TransactionImportRequestedEvent.class);

        verify(jsonMapper).convertValue(
                eventCaptor.capture(),
                org.mockito.ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        );

        TransactionImportRequestedEvent capturedEvent = eventCaptor.getValue();

        assertThat(capturedEvent.getEventId()).isNotNull();
        assertThat(capturedEvent.getEventVersion()).isEqualTo(TransactionImportRequestedEvent.CURRENT_VERSION);
        assertThat(capturedEvent.getImportId()).isEqualTo(importId);
        assertThat(capturedEvent.getAccountId()).isEqualTo(accountId);
        assertThat(capturedEvent.getUserId()).isEqualTo(userId);
        assertThat(capturedEvent.getSourceObjectKey()).isEqualTo(sourceObjectKey);
        assertThat(capturedEvent.getCorrelationId()).isEqualTo(CORRELATION_ID);
        assertThat(capturedEvent.getOccurredAt()).isNotNull();

        ArgumentCaptor<OutboxEvent> outboxEventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxEventCaptor.capture());

        OutboxEvent savedOutboxEvent = outboxEventCaptor.getValue();

        assertThat(savedOutboxEvent.getEventId()).isEqualTo(capturedEvent.getEventId());
        assertThat(savedOutboxEvent.getAggregateType()).isEqualTo("TRANSACTION_IMPORT");
        assertThat(savedOutboxEvent.getAggregateId()).isEqualTo(importId);
        assertThat(savedOutboxEvent.getEventType()).isEqualTo("TRANSACTION_IMPORT_REQUESTED");
        assertThat(savedOutboxEvent.getEventVersion()).isEqualTo(TransactionImportRequestedEvent.CURRENT_VERSION);
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

    @Test
    void writeTransactionImportRequestedWhenSerializationFailsDoesNotSaveOutboxEvent() {
        when(jsonMapper.convertValue(
                any(TransactionImportRequestedEvent.class),
                org.mockito.ArgumentMatchers.<TypeReference<Map<String, Object>>>any()
        )).thenThrow(new IllegalArgumentException("Serialization failed"));

        assertThatThrownBy(() ->
                outboxEventWriter.writeTransactionImportRequested(
                        51L,
                        12L,
                        7L,
                        "imports/7/import-123/source.csv"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Serialization failed");

        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }
}