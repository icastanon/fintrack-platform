package com.fintrack.apiservice.outbox.publisher;

import com.fintrack.apiservice.outbox.entity.OutboxEvent;
import io.awspring.cloud.sqs.operations.SqsOperations;
import io.awspring.cloud.sqs.operations.SqsSendOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsOutboxEventPublisherTest {

    private static final String TRANSACTION_QUEUE = "fintrack-transaction-processing";
    private static final String IMPORT_QUEUE = "fintrack-import-jobs";
    private static final String MESSAGE_BODY = "{\"eventVersion\":1}";

    @Mock
    private SqsOperations sqsOperations;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private SqsSendOptions<String> sqsSendOptions;

    private SqsOutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SqsOutboxEventPublisher(
                sqsOperations,
                jsonMapper,
                TRANSACTION_QUEUE,
                IMPORT_QUEUE
        );
    }

    @Test
    void publishRoutesTransactionProcessingEventToTransactionQueue() {
        Map<String, Object> payload = Map.of("eventVersion", 1);

        OutboxEvent event = OutboxEvent.create(
                UUID.randomUUID(),
                "FINANCIAL_TRANSACTION",
                41L,
                "TRANSACTION_PROCESSING_REQUESTED",
                1,
                payload
        );

        prepareSuccessfulSend(payload);

        publisher.publish(event);

        verify(sqsSendOptions).queue(TRANSACTION_QUEUE);
        verifyMessageContents(event);
    }

    @Test
    void publishRoutesTransactionImportEventToImportQueue() {
        Map<String, Object> payload = Map.of("eventVersion", 1);

        OutboxEvent event = OutboxEvent.create(
                UUID.randomUUID(),
                "TRANSACTION_IMPORT",
                51L,
                "TRANSACTION_IMPORT_REQUESTED",
                1,
                payload
        );

        prepareSuccessfulSend(payload);

        publisher.publish(event);

        verify(sqsSendOptions).queue(IMPORT_QUEUE);
        verifyMessageContents(event);
    }

    @Test
    void publishRejectsUnsupportedEventType() {
        OutboxEvent event = OutboxEvent.create(
                UUID.randomUUID(),
                "UNSUPPORTED_AGGREGATE",
                41L,
                "UNSUPPORTED_EVENT",
                1,
                Map.of("eventVersion", 1)
        );

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported outbox event type: UNSUPPORTED_EVENT");

        verifyNoInteractions(jsonMapper, sqsOperations);
    }

    @Test
    void publishWhenPayloadSerializationFailsDoesNotSendMessage() {
        Map<String, Object> payload = Map.of("eventVersion", 1);

        OutboxEvent event = OutboxEvent.create(
                UUID.randomUUID(),
                "TRANSACTION_IMPORT",
                51L,
                "TRANSACTION_IMPORT_REQUESTED",
                1,
                payload
        );

        JacksonException jacksonException = mock(JacksonException.class);
        when(jsonMapper.writeValueAsString(payload)).thenThrow(jacksonException);

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize outbox event payload: " + event.getEventId())
                .hasCause(jacksonException);

        verifyNoInteractions(sqsOperations);
    }

    private void prepareSuccessfulSend(Map<String, Object> payload) {
        when(jsonMapper.writeValueAsString(payload)).thenReturn(MESSAGE_BODY);
        when(sqsSendOptions.queue(anyString())).thenReturn(sqsSendOptions);
        when(sqsSendOptions.payload(anyString())).thenReturn(sqsSendOptions);
        when(sqsSendOptions.header(anyString(), any())).thenReturn(sqsSendOptions);

        doAnswer(invocation -> {
            Consumer<SqsSendOptions<String>> options = invocation.getArgument(0);
            options.accept(sqsSendOptions);
            return null;
        }).when(sqsOperations).send(any());
    }

    private void verifyMessageContents(OutboxEvent event) {
        verify(sqsSendOptions).payload(MESSAGE_BODY);
        verify(sqsSendOptions).header("eventId", event.getEventId().toString());
        verify(sqsSendOptions).header("eventType", event.getEventType());
        verify(sqsSendOptions).header("eventVersion", event.getEventVersion().toString());
        verify(sqsSendOptions).header("aggregateType", event.getAggregateType());
        verify(sqsSendOptions).header("aggregateId", event.getAggregateId().toString());
    }
}