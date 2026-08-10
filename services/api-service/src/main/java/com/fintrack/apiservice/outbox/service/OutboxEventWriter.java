package com.fintrack.apiservice.outbox.service;

import com.fintrack.apiservice.common.correlation.CorrelationIdFilter;
import com.fintrack.apiservice.outbox.entity.OutboxEvent;
import com.fintrack.apiservice.outbox.repository.OutboxEventRepository;
import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.eventcontracts.TransactionProcessingReason;
import com.fintrack.eventcontracts.TransactionProcessingRequestEvent;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxEventWriter {

    private static final String TRANSACTION_AGGREGATE_TYPE = "FINANCIAL_TRANSACTION";
    private static final String TRANSACTION_PROCESSING_REQUESTED_EVENT_TYPE = "TRANSACTION_PROCESSING_REQUESTED";

    private static final String IMPORT_AGGREGATE_TYPE = "TRANSACTION_IMPORT";
    private static final String IMPORT_REQUESTED_EVENT_TYPE = "TRANSACTION_IMPORT_REQUESTED";

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    public OutboxEventWriter(OutboxEventRepository outboxEventRepository, JsonMapper jsonMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void writeTransactionProcessingRequested(Long transactionId, Long userId,
                                                    TransactionProcessingReason reason) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

        TransactionProcessingRequestEvent event = TransactionProcessingRequestEvent.create(
                eventId,
                transactionId,
                userId,
                reason,
                correlationId,
                occurredAt
        );

        Map<String, Object> payload = jsonMapper.convertValue(event, new TypeReference<Map<String, Object>>() {});

        OutboxEvent outboxEvent = OutboxEvent.create(
                eventId,
                TRANSACTION_AGGREGATE_TYPE,
                transactionId,
                TRANSACTION_PROCESSING_REQUESTED_EVENT_TYPE,
                event.getEventVersion(),
                payload
        );

        outboxEventRepository.save(outboxEvent);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void writeTransactionImportRequested(Long importId,
                                                Long accountId,
                                                Long userId,
                                                String sourceObjectKey) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);

        TransactionImportRequestedEvent event = TransactionImportRequestedEvent.create(
                eventId,
                importId,
                accountId,
                userId,
                sourceObjectKey,
                correlationId,
                occurredAt
        );

        Map<String, Object> payload = jsonMapper.convertValue(event, new TypeReference<Map<String, Object>>() {});

        OutboxEvent outboxEvent = OutboxEvent.create(
                eventId,
                IMPORT_AGGREGATE_TYPE,
                importId,
                IMPORT_REQUESTED_EVENT_TYPE,
                event.getEventVersion(),
                payload
        );

        outboxEventRepository.save(outboxEvent);
    }
}