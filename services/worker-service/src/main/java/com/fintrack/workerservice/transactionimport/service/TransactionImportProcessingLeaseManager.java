package com.fintrack.workerservice.transactionimport.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.entity.TransactionImport;
import com.fintrack.workerservice.transactionimport.entity.TransactionImportStatus;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportNotFoundException;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingLeaseAcquisition;
import com.fintrack.workerservice.transactionimport.repository.TransactionImportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class TransactionImportProcessingLeaseManager {

    private final TransactionImportRepository transactionImportRepository;
    private final Duration leaseDuration;

    public TransactionImportProcessingLeaseManager(TransactionImportRepository transactionImportRepository,
                                                   @Value("${fintrack.batch.import-processing-lease-duration}")
                                                   Duration leaseDuration) {
        Objects.requireNonNull(leaseDuration, "Processing lease duration is required");

        if (leaseDuration.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException(
                    "Processing lease duration must be at least one second"
            );
        }

        this.transactionImportRepository = transactionImportRepository;
        this.leaseDuration = leaseDuration;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionImportProcessingLeaseAcquisition acquire(TransactionImportRequestedEvent event) {
        Objects.requireNonNull(event, "Transaction import requested event is required");

        TransactionImport transactionImport = transactionImportRepository
                .findByIdAndAccountIdAndUserIdForUpdate(
                        event.getImportId(),
                        event.getAccountId(),
                        event.getUserId()
                )
                .orElseThrow(() -> new TransactionImportNotFoundException(
                        event.getImportId(),
                        event.getAccountId(),
                        event.getUserId()
                ));

        Instant claimedAt = transactionImportRepository.getCurrentDatabaseTime();

        if (transactionImport.getStatus() == TransactionImportStatus.COMPLETED) {
            return TransactionImportProcessingLeaseAcquisition.alreadyCompleted();
        }

        if (transactionImport.hasActiveProcessingLease(claimedAt)) {
            return TransactionImportProcessingLeaseAcquisition.activeLease();
        }

        String processingOwner = UUID.randomUUID().toString();
        Instant leaseExpiresAt = claimedAt.plus(leaseDuration);
        long fencingToken = transactionImport.claimProcessingLease(
                processingOwner,
                claimedAt,
                leaseExpiresAt
        );

        TransactionImportProcessingAttempt processingAttempt =
                new TransactionImportProcessingAttempt(
                        event.getEventId(),
                        event.getImportId(),
                        event.getAccountId(),
                        event.getUserId(),
                        processingOwner,
                        fencingToken
                );

        return TransactionImportProcessingLeaseAcquisition.acquired(processingAttempt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renew(TransactionImportProcessingAttempt processingAttempt) {
        Objects.requireNonNull(processingAttempt, "Processing attempt is required");

        return transactionImportRepository.renewProcessingLease(
                processingAttempt.getImportId(),
                processingAttempt.getAccountId(),
                processingAttempt.getUserId(),
                processingAttempt.getProcessingOwner(),
                processingAttempt.getFencingToken(),
                leaseDuration.getSeconds()
        ) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean release(TransactionImportProcessingAttempt processingAttempt) {
        Objects.requireNonNull(processingAttempt, "Processing attempt is required");

        return transactionImportRepository.releaseProcessingLease(
                processingAttempt.getImportId(),
                processingAttempt.getAccountId(),
                processingAttempt.getUserId(),
                processingAttempt.getProcessingOwner(),
                processingAttempt.getFencingToken()
        ) == 1;
    }
}