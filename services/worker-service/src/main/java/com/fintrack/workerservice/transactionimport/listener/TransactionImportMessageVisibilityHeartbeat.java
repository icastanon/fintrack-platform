package com.fintrack.workerservice.transactionimport.listener;

import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import com.fintrack.workerservice.transactionimport.service.TransactionImportProcessingLeaseManager;
import io.awspring.cloud.sqs.listener.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class TransactionImportMessageVisibilityHeartbeat {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TransactionImportMessageVisibilityHeartbeat.class);

    private final TaskScheduler taskScheduler;
    private final TransactionImportProcessingLeaseManager processingLeaseManager;
    private final int visibilityExtensionSeconds;
    private final Duration heartbeatInterval;

    public TransactionImportMessageVisibilityHeartbeat(
            @Qualifier("transactionImportVisibilityTaskScheduler") TaskScheduler taskScheduler,
            TransactionImportProcessingLeaseManager processingLeaseManager,
            @Value("${fintrack.sqs.import-jobs-visibility-extension-seconds}") int visibilityExtensionSeconds,
            @Value("${fintrack.sqs.import-jobs-visibility-heartbeat-seconds}") int heartbeatIntervalSeconds) {
        if (visibilityExtensionSeconds <= 0) {
            throw new IllegalArgumentException("Visibility extension must be positive");
        }

        if (heartbeatIntervalSeconds <= 0 || heartbeatIntervalSeconds >= visibilityExtensionSeconds) {
            throw new IllegalArgumentException(
                    "Visibility heartbeat must be positive and shorter than the visibility extension"
            );
        }

        this.taskScheduler = taskScheduler;
        this.processingLeaseManager = processingLeaseManager;
        this.visibilityExtensionSeconds = visibilityExtensionSeconds;
        this.heartbeatInterval = Duration.ofSeconds(heartbeatIntervalSeconds);
    }

    public RunningHeartbeat start(Visibility visibility, TransactionImportProcessingAttempt processingAttempt) {
        Objects.requireNonNull(visibility, "SQS message visibility is required");
        Objects.requireNonNull(processingAttempt, "Processing attempt is required");

        try {
            visibility.changeTo(visibilityExtensionSeconds);

            AtomicBoolean processingLeaseLost = new AtomicBoolean(false);
            Instant firstHeartbeat = taskScheduler.getClock().instant().plus(heartbeatInterval);

            ScheduledFuture<?> scheduledTask = taskScheduler.scheduleAtFixedRate(
                    () -> performHeartbeat(visibility, processingAttempt, processingLeaseLost),
                    firstHeartbeat,
                    heartbeatInterval
            );

            return new RunningHeartbeat(
                    scheduledTask,
                    () -> releaseProcessingLease(processingAttempt),
                    processingLeaseLost
            );
        } catch (RuntimeException exception) {
            releaseProcessingLease(processingAttempt);
            throw exception;
        }
    }

    private void performHeartbeat(Visibility visibility,
                                  TransactionImportProcessingAttempt processingAttempt,
                                  AtomicBoolean processingLeaseLost) {
        if (processingLeaseLost.get()) {
            return;
        }

        if (!renewProcessingLease(processingAttempt)) {
            processingLeaseLost.set(true);
            return;
        }

        extendVisibility(visibility, processingAttempt);
    }

    private boolean renewProcessingLease(TransactionImportProcessingAttempt processingAttempt) {
        try {
            boolean renewed = processingLeaseManager.renew(processingAttempt);

            if (!renewed) {
                LOGGER.error(
                        "Lost transaction-import processing lease: eventId={}, importId={}, processingOwner={}, fencingToken={}",
                        processingAttempt.getEventId(),
                        processingAttempt.getImportId(),
                        processingAttempt.getProcessingOwner(),
                        processingAttempt.getFencingToken()
                );
            }

            return renewed;
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Failed to renew transaction-import processing lease: eventId={}, importId={}, processingOwner={}, fencingToken={}",
                    processingAttempt.getEventId(),
                    processingAttempt.getImportId(),
                    processingAttempt.getProcessingOwner(),
                    processingAttempt.getFencingToken(),
                    exception
            );

            return false;
        }
    }

    private void extendVisibility(Visibility visibility, TransactionImportProcessingAttempt processingAttempt) {
        try {
            visibility.changeTo(visibilityExtensionSeconds);

            LOGGER.debug(
                    "Extended transaction-import message visibility: eventId={}, importId={}, visibilitySeconds={}",
                    processingAttempt.getEventId(),
                    processingAttempt.getImportId(),
                    visibilityExtensionSeconds
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Failed to extend transaction-import message visibility: eventId={}, importId={}",
                    processingAttempt.getEventId(),
                    processingAttempt.getImportId(),
                    exception
            );
        }
    }

    private void releaseProcessingLease(TransactionImportProcessingAttempt processingAttempt) {
        try {
            boolean released = processingLeaseManager.release(processingAttempt);

            if (!released) {
                LOGGER.warn(
                        "Transaction-import processing lease was not released because ownership had changed: eventId={}, importId={}, processingOwner={}, fencingToken={}",
                        processingAttempt.getEventId(),
                        processingAttempt.getImportId(),
                        processingAttempt.getProcessingOwner(),
                        processingAttempt.getFencingToken()
                );
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Failed to release transaction-import processing lease: eventId={}, importId={}, processingOwner={}, fencingToken={}",
                    processingAttempt.getEventId(),
                    processingAttempt.getImportId(),
                    processingAttempt.getProcessingOwner(),
                    processingAttempt.getFencingToken(),
                    exception
            );
        }
    }

    public static final class RunningHeartbeat implements AutoCloseable {

        private final ScheduledFuture<?> scheduledTask;
        private final Runnable closeAction;
        private final AtomicBoolean processingLeaseLost;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private RunningHeartbeat(ScheduledFuture<?> scheduledTask,
                                 Runnable closeAction,
                                 AtomicBoolean processingLeaseLost) {
            this.scheduledTask = scheduledTask;
            this.closeAction = closeAction;
            this.processingLeaseLost = processingLeaseLost;
        }

        public boolean hasLostProcessingLease() {
            return processingLeaseLost.get();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                scheduledTask.cancel(false);
                closeAction.run();
            }
        }
    }
}