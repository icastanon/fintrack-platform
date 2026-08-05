package com.fintrack.apiservice.outbox.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.fintrack.apiservice.outbox.entity.OutboxEvent;
import com.fintrack.apiservice.outbox.repository.OutboxEventRepository;
import com.fintrack.eventcontracts.TransactionCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxEventWriter {

    private static final String TRANSACTION_AGGREGATE_TYPE = "FINANCIAL_TRANSACTION";
    private static final String TRANSACTION_CREATED_EVENT_TYPE = "TRANSACTION_CREATED";

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    public OutboxEventWriter(OutboxEventRepository outboxEventRepository, JsonMapper jsonMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void writeTransactionCreated(Long transactionId, Long userId) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        TransactionCreatedEvent event = TransactionCreatedEvent.create(eventId, transactionId, userId, occurredAt);
        Map<String, Object> payload = jsonMapper.convertValue(event, new TypeReference<Map<String, Object>>() {});

        OutboxEvent outboxEvent = OutboxEvent.create(
                eventId,
                TRANSACTION_AGGREGATE_TYPE,
                transactionId,
                TRANSACTION_CREATED_EVENT_TYPE,
                event.getEventVersion(),
                payload
        );

        outboxEventRepository.save(outboxEvent);
    }
}