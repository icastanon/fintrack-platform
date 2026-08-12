package com.fintrack.workerservice.transactionimport.listener;

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
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

@Component
public class TransactionImportMessageVisibilityHeartbeat {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(TransactionImportMessageVisibilityHeartbeat.class);

    private final TaskScheduler taskScheduler;
    private final int visibilityExtensionSeconds;
    private final Duration heartbeatInterval;

    public TransactionImportMessageVisibilityHeartbeat(
            @Qualifier("transactionImportVisibilityTaskScheduler") TaskScheduler taskScheduler,
            @Value("${fintrack.sqs.import-jobs-visibility-extension-seconds}") int visibilityExtensionSeconds,
            @Value("${fintrack.sqs.import-jobs-visibility-heartbeat-seconds}") int heartbeatIntervalSeconds) {
        if (visibilityExtensionSeconds <= 0) {
            throw new IllegalArgumentException("Visibility extension must be positive");
        }

        if (heartbeatIntervalSeconds <= 0 ||
                heartbeatIntervalSeconds >= visibilityExtensionSeconds) {
            throw new IllegalArgumentException(
                    "Visibility heartbeat must be positive and shorter than the visibility extension");
        }

        this.taskScheduler = taskScheduler;
        this.visibilityExtensionSeconds = visibilityExtensionSeconds;
        this.heartbeatInterval = Duration.ofSeconds(heartbeatIntervalSeconds);
    }

    public RunningHeartbeat start(Visibility visibility, UUID eventId, Long importId) {
        Objects.requireNonNull(visibility, "SQS message visibility is required");
        Objects.requireNonNull(eventId, "Event ID is required");
        Objects.requireNonNull(importId, "Import ID is required");

        visibility.changeTo(visibilityExtensionSeconds);

        Instant firstHeartbeat = taskScheduler.getClock().instant().plus(heartbeatInterval);

        ScheduledFuture<?> scheduledTask = taskScheduler.scheduleAtFixedRate(
                () -> extendVisibility(visibility, eventId, importId),
                firstHeartbeat,
                heartbeatInterval
        );

        return new RunningHeartbeat(scheduledTask);
    }

    private void extendVisibility(Visibility visibility, UUID eventId, Long importId) {
        try {
            visibility.changeTo(visibilityExtensionSeconds);

            LOGGER.debug(
                    "Extended transaction-import message visibility: eventId={}, importId={}, visibilitySeconds={}",
                    eventId,
                    importId,
                    visibilityExtensionSeconds
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Failed to extend transaction-import message visibility: eventId={}, importId={}",
                    eventId,
                    importId,
                    exception
            );
        }
    }

    public static final class RunningHeartbeat implements AutoCloseable {

        private final ScheduledFuture<?> scheduledTask;

        private RunningHeartbeat(ScheduledFuture<?> scheduledTask) {
            this.scheduledTask = scheduledTask;
        }

        @Override
        public void close() {
            scheduledTask.cancel(false);
        }
    }
}