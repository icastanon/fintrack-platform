package com.fintrack.apiservice.outbox.publisher;

import com.fintrack.apiservice.outbox.entity.OutboxEvent;

/*
Purpose of interface and implementation architecture: it shows that the application is not tied to a specific vendor.
Today we use SQS but in the future we could choose other vendors such as Kafka, or RabbitMQ. All we would need is
another implementation with vendor specific properties and logic.
 */
public interface OutboxEventPublisher {

    void publish(OutboxEvent event);
}