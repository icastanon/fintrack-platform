package com.fintrack.apiservice.outbox.publisher;

import com.fintrack.apiservice.outbox.entity.OutboxEvent;
import io.awspring.cloud.sqs.operations.SqsOperations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SqsOutboxEventPublisher implements OutboxEventPublisher {

    private static final String TRANSACTION_CREATED_EVENT_TYPE = "TRANSACTION_CREATED";

    private final SqsOperations sqsOperations;
    private final JsonMapper jsonMapper;
    private final String transactionProcessingQueue;

    public SqsOutboxEventPublisher(SqsOperations sqsOperations,
                                   JsonMapper jsonMapper,
                                   @Value("${fintrack.sqs.transaction-processing-queue}")
                                   String transactionProcessingQueue) {
        this.sqsOperations = sqsOperations;
        this.jsonMapper = jsonMapper;
        this.transactionProcessingQueue = transactionProcessingQueue;
    }

    @Override
    public void publish(OutboxEvent event) {
        String destinationQueue = resolveDestinationQueue(event.getEventType());
        String messageBody = serializePayload(event);

        sqsOperations.send(options -> options
                .queue(destinationQueue)
                .payload(messageBody)
                .header("eventId", event.getEventId().toString())
                .header("eventType", event.getEventType())
                .header("eventVersion", event.getEventVersion().toString())
                .header("aggregateType", event.getAggregateType())
                .header("aggregateId", event.getAggregateId().toString()));
    }

    private String resolveDestinationQueue(String eventType) {
        if (TRANSACTION_CREATED_EVENT_TYPE.equals(eventType)) {
            return transactionProcessingQueue;
        }

        throw new IllegalArgumentException("Unsupported outbox event type: " + eventType);
    }

    private String serializePayload(OutboxEvent event) {
        try {
            return jsonMapper.writeValueAsString(event.getPayload());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize outbox event payload: " + event.getEventId(), exception);
        }
    }
}