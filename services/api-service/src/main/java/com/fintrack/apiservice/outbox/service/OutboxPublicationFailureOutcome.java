package com.fintrack.apiservice.outbox.service;

public enum OutboxPublicationFailureOutcome {
    RETRY_SCHEDULED,
    PERMANENTLY_FAILED
}