package com.fintrack.eventcontracts;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionImportRequestedEventTest {

    @Test
    void createBuildsCurrentVersionImportRequest() {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-08-10T15:30:00Z");

        TransactionImportRequestedEvent event = TransactionImportRequestedEvent.create(
                eventId,
                41L,
                15L,
                7L,
                "imports/7/test-id/source.csv",
                "correlation-123",
                occurredAt
        );

        assertThat(event.getEventId()).isEqualTo(eventId);
        assertThat(event.getEventVersion()).isEqualTo(TransactionImportRequestedEvent.CURRENT_VERSION);
        assertThat(event.getImportId()).isEqualTo(41L);
        assertThat(event.getAccountId()).isEqualTo(15L);
        assertThat(event.getUserId()).isEqualTo(7L);
        assertThat(event.getSourceObjectKey()).isEqualTo("imports/7/test-id/source.csv");
        assertThat(event.getCorrelationId()).isEqualTo("correlation-123");
        assertThat(event.getOccurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void createUsesEventIdWhenCorrelationIdIsBlank() {
        UUID eventId = UUID.randomUUID();

        TransactionImportRequestedEvent event = TransactionImportRequestedEvent.create(
                eventId,
                41L,
                15L,
                7L,
                "imports/7/test-id/source.csv",
                "   ",
                Instant.parse("2026-08-10T15:30:00Z")
        );

        assertThat(event.getCorrelationId()).isEqualTo(eventId.toString());
    }

    @Test
    void constructorRejectsNonPositiveVersion() {
        assertThatThrownBy(() -> new TransactionImportRequestedEvent(
                UUID.randomUUID(),
                0,
                41L,
                15L,
                7L,
                "imports/7/test-id/source.csv",
                "correlation-123",
                Instant.parse("2026-08-10T15:30:00Z")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event version must be positive");
    }

    @Test
    void constructorRejectsMissingRequiredIdentifiers() {
        assertThatThrownBy(() -> new TransactionImportRequestedEvent(
                UUID.randomUUID(),
                1,
                null,
                15L,
                7L,
                "imports/7/test-id/source.csv",
                "correlation-123",
                Instant.parse("2026-08-10T15:30:00Z")
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Import ID is required");
    }

    @Test
    void constructorRejectsBlankSourceObjectKey() {
        assertThatThrownBy(() -> new TransactionImportRequestedEvent(
                UUID.randomUUID(),
                1,
                41L,
                15L,
                7L,
                "   ",
                "correlation-123",
                Instant.parse("2026-08-10T15:30:00Z")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source object key is required");
    }

    @Test
    void eventSurvivesJsonRoundTrip() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

        TransactionImportRequestedEvent original = TransactionImportRequestedEvent.create(
                UUID.fromString("3a4f7dc2-6454-47ba-9392-13cabfed3f21"),
                41L,
                15L,
                7L,
                "imports/7/test-id/source.csv",
                "correlation-123",
                Instant.parse("2026-08-10T15:30:00Z")
        );

        String json = jsonMapper.writeValueAsString(original);
        TransactionImportRequestedEvent restored =
                jsonMapper.readValue(json, TransactionImportRequestedEvent.class);

        assertThat(restored.getEventId()).isEqualTo(original.getEventId());
        assertThat(restored.getEventVersion()).isEqualTo(original.getEventVersion());
        assertThat(restored.getImportId()).isEqualTo(original.getImportId());
        assertThat(restored.getAccountId()).isEqualTo(original.getAccountId());
        assertThat(restored.getUserId()).isEqualTo(original.getUserId());
        assertThat(restored.getSourceObjectKey()).isEqualTo(original.getSourceObjectKey());
        assertThat(restored.getCorrelationId()).isEqualTo(original.getCorrelationId());
        assertThat(restored.getOccurredAt()).isEqualTo(original.getOccurredAt());
    }
}