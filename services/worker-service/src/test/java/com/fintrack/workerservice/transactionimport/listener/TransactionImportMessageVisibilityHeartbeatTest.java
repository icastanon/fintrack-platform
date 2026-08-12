package com.fintrack.workerservice.transactionimport.listener;

import io.awspring.cloud.sqs.listener.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportMessageVisibilityHeartbeatTest {

    private static final UUID EVENT_ID = UUID.fromString("8fb4e595-dbbc-4b7f-a791-8902bf5d93e1");
    private static final Long IMPORT_ID = 41L;
    private static final int VISIBILITY_EXTENSION_SECONDS = 120;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private Visibility visibility;

    @Mock
    private ScheduledFuture<?> scheduledTask;

    private TransactionImportMessageVisibilityHeartbeat heartbeat;

    @BeforeEach
    void setUp() {
        heartbeat = new TransactionImportMessageVisibilityHeartbeat(
                taskScheduler,
                VISIBILITY_EXTENSION_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS
        );
    }

    @Test
    void startExtendsVisibilityImmediatelyAndSchedulesHeartbeat() {
        when(taskScheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));

        doReturn(scheduledTask).when(taskScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                any(Instant.class),
                any(Duration.class)
        );

        TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat =
                heartbeat.start(visibility, EVENT_ID, IMPORT_ID);

        ArgumentCaptor<Instant> firstHeartbeatCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Duration> intervalCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(visibility).changeTo(VISIBILITY_EXTENSION_SECONDS);
        verify(taskScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                firstHeartbeatCaptor.capture(),
                intervalCaptor.capture()
        );

        assertThat(firstHeartbeatCaptor.getValue()).isEqualTo(NOW.plusSeconds(HEARTBEAT_INTERVAL_SECONDS));
        assertThat(intervalCaptor.getValue()).isEqualTo(Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS));

        runningHeartbeat.close();

        verify(scheduledTask).cancel(false);
    }

    @Test
    void scheduledHeartbeatExtendsVisibilityAgain() {
        when(taskScheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        doReturn(scheduledTask).when(taskScheduler).scheduleAtFixedRate(
                taskCaptor.capture(),
                any(Instant.class),
                eq(Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS))
        );

        TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat =
                heartbeat.start(visibility, EVENT_ID, IMPORT_ID);

        taskCaptor.getValue().run();

        verify(visibility, times(2)).changeTo(VISIBILITY_EXTENSION_SECONDS);

        runningHeartbeat.close();
    }

    @Test
    void scheduledHeartbeatRecordsFailureWithoutTerminatingScheduledTask() {
        when(taskScheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        doReturn(scheduledTask).when(taskScheduler).scheduleAtFixedRate(
                taskCaptor.capture(),
                any(Instant.class),
                eq(Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS))
        );

        RuntimeException cause = new IllegalStateException("SQS unavailable");

        doNothing()
                .doThrow(cause)
                .when(visibility)
                .changeTo(VISIBILITY_EXTENSION_SECONDS);

        TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat =
                heartbeat.start(visibility, EVENT_ID, IMPORT_ID);

        assertThatCode(taskCaptor.getValue()::run).doesNotThrowAnyException();

        verify(visibility, times(2)).changeTo(VISIBILITY_EXTENSION_SECONDS);
        verify(scheduledTask, never()).cancel(false);

        runningHeartbeat.close();

        verify(scheduledTask).cancel(false);
    }

    @Test
    void startPropagatesInitialVisibilityFailureWithoutSchedulingHeartbeat() {
        RuntimeException cause = new IllegalStateException("SQS unavailable");

        doThrow(cause).when(visibility).changeTo(VISIBILITY_EXTENSION_SECONDS);

        assertThatThrownBy(() -> heartbeat.start(visibility, EVENT_ID, IMPORT_ID))
                .isSameAs(cause);

        verify(taskScheduler, never()).scheduleAtFixedRate(
                any(Runnable.class),
                any(Instant.class),
                any(Duration.class)
        );
    }

    @Test
    void constructorRejectsNonPositiveVisibilityExtension() {
        assertThatThrownBy(() -> new TransactionImportMessageVisibilityHeartbeat(
                taskScheduler,
                0,
                HEARTBEAT_INTERVAL_SECONDS
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Visibility extension must be positive");

        verifyNoInteractions(taskScheduler);
    }

    @Test
    void constructorRejectsNonPositiveHeartbeatInterval() {
        assertThatThrownBy(() -> new TransactionImportMessageVisibilityHeartbeat(
                taskScheduler,
                VISIBILITY_EXTENSION_SECONDS,
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Visibility heartbeat must be positive and shorter than the visibility extension"
                );

        verifyNoInteractions(taskScheduler);
    }

    @Test
    void constructorRejectsHeartbeatThatIsNotShorterThanExtension() {
        assertThatThrownBy(() -> new TransactionImportMessageVisibilityHeartbeat(
                taskScheduler,
                VISIBILITY_EXTENSION_SECONDS,
                VISIBILITY_EXTENSION_SECONDS
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Visibility heartbeat must be positive and shorter than the visibility extension"
                );

        verifyNoInteractions(taskScheduler);
    }

    @Test
    void startRejectsNullVisibility() {
        assertThatThrownBy(() -> heartbeat.start(null, EVENT_ID, IMPORT_ID))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("SQS message visibility is required");

        verifyNoInteractions(taskScheduler);
    }

    @Test
    void startRejectsNullEventId() {
        assertThatThrownBy(() -> heartbeat.start(visibility, null, IMPORT_ID))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Event ID is required");

        verifyNoInteractions(taskScheduler, visibility);
    }

    @Test
    void startRejectsNullImportId() {
        assertThatThrownBy(() -> heartbeat.start(visibility, EVENT_ID, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Import ID is required");

        verifyNoInteractions(taskScheduler, visibility);
    }
}