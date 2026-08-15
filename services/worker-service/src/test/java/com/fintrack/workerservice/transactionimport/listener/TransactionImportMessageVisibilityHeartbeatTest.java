package com.fintrack.workerservice.transactionimport.listener;

import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import com.fintrack.workerservice.transactionimport.service.TransactionImportProcessingLeaseManager;
import io.awspring.cloud.sqs.listener.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportMessageVisibilityHeartbeatTest {

    private static final UUID EVENT_ID = UUID.fromString("8fb4e595-dbbc-4b7f-a791-8902bf5d93e1");
    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 52L;
    private static final Long USER_ID = 63L;
    private static final String PROCESSING_OWNER = "worker-a";
    private static final long FENCING_TOKEN = 7L;
    private static final int VISIBILITY_EXTENSION_SECONDS = 120;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    private static final TransactionImportProcessingAttempt PROCESSING_ATTEMPT =
            new TransactionImportProcessingAttempt(EVENT_ID,
                    IMPORT_ID,
                    ACCOUNT_ID,
                    USER_ID,
                    PROCESSING_OWNER,
                    FENCING_TOKEN);

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private TransactionImportProcessingLeaseManager processingLeaseManager;

    @Mock
    private Visibility visibility;

    @Mock
    private ScheduledFuture<?> scheduledTask;

    private TransactionImportMessageVisibilityHeartbeat heartbeat;

    @BeforeEach
    void setUp() {
        heartbeat = new TransactionImportMessageVisibilityHeartbeat(taskScheduler,
                processingLeaseManager,
                VISIBILITY_EXTENSION_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS);
    }

    @Test
    void startExtendsVisibilityImmediatelyAndSchedulesHeartbeat() {
        when(taskScheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
        doReturn(scheduledTask).when(taskScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                any(Instant.class),
                any(Duration.class)
        );
        when(processingLeaseManager.release(PROCESSING_ATTEMPT)).thenReturn(true);

        TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat =
                heartbeat.start(visibility, PROCESSING_ATTEMPT);

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
        assertThat(runningHeartbeat.hasLostProcessingLease()).isFalse();

        runningHeartbeat.close();

        verify(scheduledTask).cancel(false);
        verify(processingLeaseManager).release(PROCESSING_ATTEMPT);
    }

    @Test
    void heartbeatRenewsDatabaseLeaseBeforeExtendingVisibility() {
        ArgumentCaptor<Runnable> taskCaptor = arrangeScheduledTask();

        when(processingLeaseManager.renew(PROCESSING_ATTEMPT)).thenReturn(true);
        when(processingLeaseManager.release(PROCESSING_ATTEMPT)).thenReturn(true);

        TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat =
                heartbeat.start(visibility, PROCESSING_ATTEMPT);

        taskCaptor.getValue().run();

        InOrder heartbeatOrder = inOrder(visibility, processingLeaseManager);
        heartbeatOrder.verify(visibility).changeTo(VISIBILITY_EXTENSION_SECONDS);
        heartbeatOrder.verify(processingLeaseManager).renew(PROCESSING_ATTEMPT);
        heartbeatOrder.verify(visibility).changeTo(VISIBILITY_EXTENSION_SECONDS);

        assertThat(runningHeartbeat.hasLostProcessingLease()).isFalse();

        runningHeartbeat.close();

        verify(scheduledTask).cancel(false);
        verify(processingLeaseManager).release(PROCESSING_ATTEMPT);
    }

    @Test
    void heartbeatRecordsSqsFailureWithoutMarkingDatabaseLeaseAsLost() {
        ArgumentCaptor<Runnable> taskCaptor = arrangeScheduledTask();
        RuntimeException cause = new IllegalStateException("SQS unavailable");

        when(processingLeaseManager.renew(PROCESSING_ATTEMPT)).thenReturn(true);
        when(processingLeaseManager.release(PROCESSING_ATTEMPT)).thenReturn(true);
        doNothing().doThrow(cause).when(visibility).changeTo(VISIBILITY_EXTENSION_SECONDS);

        TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat =
                heartbeat.start(visibility, PROCESSING_ATTEMPT);

        assertThatCode(taskCaptor.getValue()::run).doesNotThrowAnyException();

        assertThat(runningHeartbeat.hasLostProcessingLease()).isFalse();

        verify(processingLeaseManager).renew(PROCESSING_ATTEMPT);
        verify(visibility, times(2)).changeTo(VISIBILITY_EXTENSION_SECONDS);
        verify(scheduledTask, never()).cancel(false);

        runningHeartbeat.close();

        verify(scheduledTask).cancel(false);
        verify(processingLeaseManager).release(PROCESSING_ATTEMPT);
    }

    @Test
    void heartbeatStopsExtendingVisibilityWhenLeaseIsLost() {
        ArgumentCaptor<Runnable> taskCaptor = arrangeScheduledTask();

        when(processingLeaseManager.renew(PROCESSING_ATTEMPT)).thenReturn(false);
        when(processingLeaseManager.release(PROCESSING_ATTEMPT)).thenReturn(false);

        TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat =
                heartbeat.start(visibility, PROCESSING_ATTEMPT);

        taskCaptor.getValue().run();
        taskCaptor.getValue().run();

        assertThat(runningHeartbeat.hasLostProcessingLease()).isTrue();

        verify(processingLeaseManager).renew(PROCESSING_ATTEMPT);
        verify(visibility).changeTo(VISIBILITY_EXTENSION_SECONDS);

        runningHeartbeat.close();

        verify(processingLeaseManager).release(PROCESSING_ATTEMPT);
    }

    @Test
    void heartbeatStopsExtendingVisibilityWhenLeaseRenewalThrows() {
        ArgumentCaptor<Runnable> taskCaptor = arrangeScheduledTask();
        RuntimeException cause = new IllegalStateException("PostgreSQL unavailable");

        when(processingLeaseManager.renew(PROCESSING_ATTEMPT)).thenThrow(cause);
        when(processingLeaseManager.release(PROCESSING_ATTEMPT)).thenReturn(true);

        TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat =
                heartbeat.start(visibility, PROCESSING_ATTEMPT);

        assertThatCode(taskCaptor.getValue()::run).doesNotThrowAnyException();
        taskCaptor.getValue().run();

        assertThat(runningHeartbeat.hasLostProcessingLease()).isTrue();

        verify(processingLeaseManager).renew(PROCESSING_ATTEMPT);
        verify(visibility).changeTo(VISIBILITY_EXTENSION_SECONDS);

        runningHeartbeat.close();

        verify(processingLeaseManager).release(PROCESSING_ATTEMPT);
    }

    @Test
    void closeCancelsHeartbeatAndReleasesLeaseOnlyOnce() {
        arrangeScheduledTask();

        when(processingLeaseManager.release(PROCESSING_ATTEMPT)).thenReturn(true);

        TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat =
                heartbeat.start(visibility, PROCESSING_ATTEMPT);

        runningHeartbeat.close();
        runningHeartbeat.close();

        verify(scheduledTask).cancel(false);
        verify(processingLeaseManager).release(PROCESSING_ATTEMPT);
    }

    @Test
    void closeDoesNotThrowWhenLeaseReleaseFails() {
        arrangeScheduledTask();

        RuntimeException cause = new IllegalStateException("PostgreSQL unavailable");
        when(processingLeaseManager.release(PROCESSING_ATTEMPT)).thenThrow(cause);

        TransactionImportMessageVisibilityHeartbeat.RunningHeartbeat runningHeartbeat =
                heartbeat.start(visibility, PROCESSING_ATTEMPT);

        assertThatCode(runningHeartbeat::close).doesNotThrowAnyException();

        verify(scheduledTask).cancel(false);
        verify(processingLeaseManager).release(PROCESSING_ATTEMPT);
    }

    @Test
    void startReleasesLeaseWhenInitialVisibilityExtensionFails() {
        RuntimeException cause = new IllegalStateException("SQS unavailable");

        doThrow(cause).when(visibility).changeTo(VISIBILITY_EXTENSION_SECONDS);
        when(processingLeaseManager.release(PROCESSING_ATTEMPT)).thenReturn(true);

        assertThatThrownBy(() -> heartbeat.start(visibility, PROCESSING_ATTEMPT)).isSameAs(cause);

        verify(processingLeaseManager).release(PROCESSING_ATTEMPT);
        verify(taskScheduler, never()).scheduleAtFixedRate(
                any(Runnable.class),
                any(Instant.class),
                any(Duration.class)
        );
    }

    @Test
    void startReleasesLeaseWhenSchedulingFails() {
        RuntimeException cause = new IllegalStateException("Scheduler unavailable");

        when(taskScheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));
        doThrow(cause).when(taskScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                any(Instant.class),
                any(Duration.class)
        );
        when(processingLeaseManager.release(PROCESSING_ATTEMPT)).thenReturn(true);

        assertThatThrownBy(() -> heartbeat.start(visibility, PROCESSING_ATTEMPT)).isSameAs(cause);

        verify(visibility).changeTo(VISIBILITY_EXTENSION_SECONDS);
        verify(processingLeaseManager).release(PROCESSING_ATTEMPT);
    }

    @Test
    void constructorRejectsNonPositiveVisibilityExtension() {
        assertThatThrownBy(() -> new TransactionImportMessageVisibilityHeartbeat(
                taskScheduler,
                processingLeaseManager,
                0,
                HEARTBEAT_INTERVAL_SECONDS
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Visibility extension must be positive");

        verifyNoInteractions(taskScheduler, processingLeaseManager);
    }

    @Test
    void constructorRejectsNonPositiveHeartbeatInterval() {
        assertThatThrownBy(() -> new TransactionImportMessageVisibilityHeartbeat(
                taskScheduler,
                processingLeaseManager,
                VISIBILITY_EXTENSION_SECONDS,
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Visibility heartbeat must be positive and shorter than the visibility extension");

        verifyNoInteractions(taskScheduler, processingLeaseManager);
    }

    @Test
    void constructorRejectsHeartbeatThatIsNotShorterThanExtension() {
        assertThatThrownBy(() -> new TransactionImportMessageVisibilityHeartbeat(
                taskScheduler,
                processingLeaseManager,
                VISIBILITY_EXTENSION_SECONDS,
                VISIBILITY_EXTENSION_SECONDS
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Visibility heartbeat must be positive and shorter than the visibility extension");

        verifyNoInteractions(taskScheduler, processingLeaseManager);
    }

    @Test
    void startRejectsNullVisibility() {
        assertThatThrownBy(() -> heartbeat.start(null, PROCESSING_ATTEMPT))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("SQS message visibility is required");

        verifyNoInteractions(taskScheduler, processingLeaseManager);
    }

    @Test
    void startRejectsNullProcessingAttempt() {
        assertThatThrownBy(() -> heartbeat.start(visibility, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Processing attempt is required");

        verifyNoInteractions(taskScheduler, processingLeaseManager, visibility);
    }

    private ArgumentCaptor<Runnable> arrangeScheduledTask() {
        when(taskScheduler.getClock()).thenReturn(Clock.fixed(NOW, ZoneOffset.UTC));

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        doReturn(scheduledTask).when(taskScheduler).scheduleAtFixedRate(
                taskCaptor.capture(),
                any(Instant.class),
                eq(Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS))
        );

        return taskCaptor;
    }
}