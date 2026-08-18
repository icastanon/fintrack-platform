package com.fintrack.apiservice.outbox.scheduler;

import com.fintrack.apiservice.outbox.metrics.OutboxRelayMetrics;
import com.fintrack.apiservice.outbox.service.OutboxEventLifecycleService;
import com.fintrack.apiservice.outbox.service.OutboxRelayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    private static final int BATCH_SIZE = 10;
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(2);

    @Mock
    private OutboxRelayService outboxRelayService;

    @Mock
    private OutboxEventLifecycleService lifecycleService;

    @Mock
    private OutboxRelayMetrics outboxRelayMetrics;

    private OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OutboxRelayScheduler(
                outboxRelayService,
                lifecycleService,
                outboxRelayMetrics,
                BATCH_SIZE,
                MAX_ATTEMPTS,
                RETRY_DELAY,
                CLAIM_TIMEOUT
        );
    }

    @Test
    void runRelayCycleRecordsRecoveredClaimsAndRelaysAvailableEvents() {
        when(lifecycleService.recoverStaleClaims(CLAIM_TIMEOUT, BATCH_SIZE)).thenReturn(2);
        when(outboxRelayService.relayAvailableEvents(
                eq(BATCH_SIZE),
                startsWith("api-service-"),
                eq(MAX_ATTEMPTS),
                eq(RETRY_DELAY)
        )).thenReturn(3);

        scheduler.runRelayCycle();

        verify(outboxRelayMetrics).recordStaleRecovered(2);
        verify(outboxRelayService).relayAvailableEvents(
                eq(BATCH_SIZE),
                startsWith("api-service-"),
                eq(MAX_ATTEMPTS),
                eq(RETRY_DELAY)
        );
        verify(outboxRelayMetrics, never()).recordRelayFailure();
    }

    @Test
    void runRelayCycleRecordsFailureWhenRelayThrows() {
        when(lifecycleService.recoverStaleClaims(CLAIM_TIMEOUT, BATCH_SIZE)).thenReturn(0);
        when(outboxRelayService.relayAvailableEvents(
                eq(BATCH_SIZE),
                anyString(),
                eq(MAX_ATTEMPTS),
                eq(RETRY_DELAY)
        )).thenThrow(new IllegalStateException("Database unavailable"));

        scheduler.runRelayCycle();

        verify(outboxRelayMetrics).recordStaleRecovered(0);
        verify(outboxRelayMetrics).recordRelayFailure();
    }

    @Test
    void runRelayCycleRecordsFailureWhenStaleRecoveryThrows() {
        when(lifecycleService.recoverStaleClaims(CLAIM_TIMEOUT, BATCH_SIZE))
                .thenThrow(new IllegalStateException("Database unavailable"));

        scheduler.runRelayCycle();

        verify(outboxRelayMetrics).recordRelayFailure();
        verify(outboxRelayMetrics, never()).recordStaleRecovered(anyInt());
        verifyNoInteractions(outboxRelayService);
    }
}