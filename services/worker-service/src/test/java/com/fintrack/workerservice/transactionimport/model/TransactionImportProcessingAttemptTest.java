package com.fintrack.workerservice.transactionimport.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionImportProcessingAttemptTest {

    private static final UUID EVENT_ID =
            UUID.fromString("a35c1351-d184-4014-b886-c1fbb8c7eec2");

    @Test
    void constructorStoresValidatedAttemptInformation() {
        TransactionImportProcessingAttempt attempt =
                new TransactionImportProcessingAttempt(
                        EVENT_ID,
                        41L,
                        22L,
                        9L,
                        "  worker-attempt-123  ",
                        3L
                );

        assertThat(attempt.getEventId()).isEqualTo(EVENT_ID);
        assertThat(attempt.getImportId()).isEqualTo(41L);
        assertThat(attempt.getAccountId()).isEqualTo(22L);
        assertThat(attempt.getUserId()).isEqualTo(9L);
        assertThat(attempt.getProcessingOwner()).isEqualTo("worker-attempt-123");
        assertThat(attempt.getFencingToken()).isEqualTo(3L);
    }

    @Test
    void constructorRejectsMissingEventId() {
        assertThatThrownBy(() ->
                new TransactionImportProcessingAttempt(
                        null,
                        41L,
                        22L,
                        9L,
                        "worker-attempt-123",
                        3L
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event ID is required");
    }

    @Test
    void constructorRejectsNonPositiveImportId() {
        assertThatThrownBy(() ->
                new TransactionImportProcessingAttempt(
                        EVENT_ID,
                        0L,
                        22L,
                        9L,
                        "worker-attempt-123",
                        3L
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Import ID must be positive");
    }

    @Test
    void constructorRejectsBlankProcessingOwner() {
        assertThatThrownBy(() ->
                new TransactionImportProcessingAttempt(
                        EVENT_ID,
                        41L,
                        22L,
                        9L,
                        " ",
                        3L
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Processing owner is required");
    }

    @Test
    void constructorRejectsNonPositiveFencingToken() {
        assertThatThrownBy(() ->
                new TransactionImportProcessingAttempt(
                        EVENT_ID,
                        41L,
                        22L,
                        9L,
                        "worker-attempt-123",
                        0L
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Processing fencing token must be positive");
    }
}