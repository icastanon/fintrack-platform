package com.fintrack.workerservice.transactionimport.listener;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.exception.UnsupportedTransactionImportRequestedEventVersionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionImportRequestedEventListenerTest {

    private static final String CORRELATION_ID = "import-request-123";

    private final TransactionImportRequestedEventListener listener = new TransactionImportRequestedEventListener();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void handleAcceptsCurrentEventVersionAndClearsCorrelationId() {
        TransactionImportRequestedEvent event = TransactionImportRequestedEvent.create(
                UUID.randomUUID(),
                41L,
                15L,
                7L,
                "imports/7/41/source.csv",
                CORRELATION_ID,
                Instant.parse("2026-08-10T20:00:00Z")
        );

        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void handleRejectsUnsupportedEventVersionAndClearsCorrelationId() {
        TransactionImportRequestedEvent event = new TransactionImportRequestedEvent(
                UUID.randomUUID(),
                TransactionImportRequestedEvent.CURRENT_VERSION + 1,
                41L,
                15L,
                7L,
                "imports/7/41/source.csv",
                CORRELATION_ID,
                Instant.parse("2026-08-10T20:00:00Z")
        );

        assertThatThrownBy(() -> listener.handle(event))
                .isInstanceOf(UnsupportedTransactionImportRequestedEventVersionException.class)
                .hasMessage(
                        "Unsupported transaction-import request event version: 2. Supported version: 1"
                );

        assertThat(MDC.get("correlationId")).isNull();
    }
}