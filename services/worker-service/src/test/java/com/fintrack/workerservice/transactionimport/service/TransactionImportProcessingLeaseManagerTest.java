package com.fintrack.workerservice.transactionimport.service;

import com.fintrack.eventcontracts.TransactionImportRequestedEvent;
import com.fintrack.workerservice.transactionimport.entity.TransactionImport;
import com.fintrack.workerservice.transactionimport.entity.TransactionImportStatus;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportNotFoundException;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportProcessingLeaseLostException;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingAttempt;
import com.fintrack.workerservice.transactionimport.model.TransactionImportProcessingLeaseAcquisition;
import com.fintrack.workerservice.transactionimport.repository.TransactionImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportProcessingLeaseManagerTest {

    private static final UUID EVENT_ID = UUID.fromString("a35c1351-d184-4014-b886-c1fbb8c7eec2");
    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 22L;
    private static final Long USER_ID = 9L;
    private static final long FENCING_TOKEN = 3L;
    private static final String PROCESSING_OWNER = "worker-attempt-123";
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    private static final Instant CLAIMED_AT = Instant.parse("2026-08-14T12:00:00Z");
    private static final Instant LEASE_EXPIRES_AT = CLAIMED_AT.plus(LEASE_DURATION);

    @Mock
    private TransactionImportRepository transactionImportRepository;

    @Mock
    private TransactionImport transactionImport;

    private TransactionImportProcessingLeaseManager leaseManager;

    @BeforeEach
    void setUp() {
        leaseManager = new TransactionImportProcessingLeaseManager(transactionImportRepository,
                LEASE_DURATION);
    }

    @Test
    void acquireCreatesProcessingAttemptWhenLeaseIsAvailable() {
        matchingImportExistsForUpdate();

        when(transactionImportRepository.getCurrentDatabaseTime()).thenReturn(CLAIMED_AT);
        when(transactionImport.getStatus()).thenReturn(TransactionImportStatus.QUEUED);
        when(transactionImport.hasActiveProcessingLease(CLAIMED_AT)).thenReturn(false);
        when(transactionImport.claimProcessingLease(anyString(),
                eq(CLAIMED_AT),
                eq(LEASE_EXPIRES_AT)))
                .thenReturn(FENCING_TOKEN);

        TransactionImportProcessingLeaseAcquisition acquisition = leaseManager.acquire(event());

        assertThat(acquisition.getOutcome())
                .isEqualTo(TransactionImportProcessingLeaseAcquisition.Outcome.ACQUIRED);
        assertThat(acquisition.isAcquired()).isTrue();

        TransactionImportProcessingAttempt processingAttempt =
                acquisition.getProcessingAttempt();

        assertThat(processingAttempt.getEventId()).isEqualTo(EVENT_ID);
        assertThat(processingAttempt.getImportId()).isEqualTo(IMPORT_ID);
        assertThat(processingAttempt.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(processingAttempt.getUserId()).isEqualTo(USER_ID);
        assertThat(processingAttempt.getFencingToken()).isEqualTo(FENCING_TOKEN);

        ArgumentCaptor<String> processingOwnerCaptor = ArgumentCaptor.forClass(String.class);

        verify(transactionImport).claimProcessingLease(processingOwnerCaptor.capture(),
                eq(CLAIMED_AT),
                eq(LEASE_EXPIRES_AT));

        assertThat(processingAttempt.getProcessingOwner())
                .isEqualTo(processingOwnerCaptor.getValue());

        assertThatCode(() -> UUID.fromString(processingOwnerCaptor.getValue()))
                .doesNotThrowAnyException();
    }

    @Test
    void acquireReturnsActiveWhenCurrentLeaseHasNotExpired() {
        matchingImportExistsForUpdate();

        when(transactionImportRepository.getCurrentDatabaseTime()).thenReturn(CLAIMED_AT);
        when(transactionImport.getStatus()).thenReturn(TransactionImportStatus.RUNNING);
        when(transactionImport.hasActiveProcessingLease(CLAIMED_AT)).thenReturn(true);

        TransactionImportProcessingLeaseAcquisition acquisition = leaseManager.acquire(event());

        assertThat(acquisition.getOutcome())
                .isEqualTo(TransactionImportProcessingLeaseAcquisition.Outcome.ACTIVE_LEASE);
        assertThat(acquisition.getProcessingAttempt()).isNull();
        assertThat(acquisition.isAcquired()).isFalse();

        verify(transactionImport, never()).claimProcessingLease(anyString(),
                eq(CLAIMED_AT),
                eq(LEASE_EXPIRES_AT));
    }

    @Test
    void acquireReturnsCompletedWithoutChangingLease() {
        matchingImportExistsForUpdate();

        when(transactionImportRepository.getCurrentDatabaseTime()).thenReturn(CLAIMED_AT);
        when(transactionImport.getStatus()).thenReturn(TransactionImportStatus.COMPLETED);

        TransactionImportProcessingLeaseAcquisition acquisition = leaseManager.acquire(event());

        assertThat(acquisition.getOutcome())
                .isEqualTo(TransactionImportProcessingLeaseAcquisition.Outcome.ALREADY_COMPLETED);
        assertThat(acquisition.getProcessingAttempt()).isNull();
        assertThat(acquisition.isAcquired()).isFalse();

        verify(transactionImport, never()).hasActiveProcessingLease(CLAIMED_AT);
        verify(transactionImport, never()).claimProcessingLease(anyString(),
                eq(CLAIMED_AT),
                eq(LEASE_EXPIRES_AT));
    }

    @Test
    void acquireThrowsWhenImportDoesNotMatchEventOwnership() {
        when(transactionImportRepository.findByIdAndAccountIdAndUserIdForUpdate(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaseManager.acquire(event()))
                .isInstanceOf(TransactionImportNotFoundException.class)
                .hasMessage("Transaction import 41 was not found for account 22 and user 9");

        verify(transactionImportRepository, never()).getCurrentDatabaseTime();
        verifyNoInteractions(transactionImport);
    }

    @Test
    void assertActiveSucceedsWhenCurrentLeaseMatches() {
        when(transactionImportRepository.findActiveProcessingLeaseForUpdate(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                FENCING_TOKEN))
                .thenReturn(Optional.of(transactionImport));

        assertThatCode(() -> leaseManager.assertActive(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                FENCING_TOKEN))
                .doesNotThrowAnyException();
    }

    @Test
    void assertActiveThrowsWhenLeaseIsExpiredOrOwnershipHasChanged() {
        when(transactionImportRepository.findActiveProcessingLeaseForUpdate(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                FENCING_TOKEN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaseManager.assertActive(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                FENCING_TOKEN))
                .isInstanceOf(TransactionImportProcessingLeaseLostException.class)
                .hasMessage(
                        "Transaction import processing lease is no longer active: "
                                + "importId=41, processingOwner=worker-attempt-123, fencingToken=3"
                );
    }

    @Test
    void assertActiveRejectsInvalidImportIdBeforeQueryingDatabase() {
        assertThatThrownBy(() -> leaseManager.assertActive(0L,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                FENCING_TOKEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Import ID must be positive");

        verifyNoInteractions(transactionImportRepository, transactionImport);
    }

    @Test
    void assertActiveRejectsMissingProcessingOwnerBeforeQueryingDatabase() {
        assertThatThrownBy(() -> leaseManager.assertActive(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                null,
                FENCING_TOKEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Processing owner is required");

        verifyNoInteractions(transactionImportRepository, transactionImport);
    }

    @Test
    void assertActiveRejectsInvalidFencingTokenBeforeQueryingDatabase() {
        assertThatThrownBy(() -> leaseManager.assertActive(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Processing fencing token must be positive");

        verifyNoInteractions(transactionImportRepository, transactionImport);
    }

    @Test
    void renewReturnsTrueWhenCurrentOwnershipMatches() {
        TransactionImportProcessingAttempt processingAttempt = processingAttempt();

        when(transactionImportRepository.renewProcessingLease(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                FENCING_TOKEN,
                LEASE_DURATION.getSeconds()))
                .thenReturn(1);

        assertThat(leaseManager.renew(processingAttempt)).isTrue();
    }

    @Test
    void renewReturnsFalseWhenOwnershipHasChanged() {
        TransactionImportProcessingAttempt processingAttempt = processingAttempt();

        when(transactionImportRepository.renewProcessingLease(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                FENCING_TOKEN,
                LEASE_DURATION.getSeconds()))
                .thenReturn(0);

        assertThat(leaseManager.renew(processingAttempt)).isFalse();
    }

    @Test
    void releaseReturnsTrueWhenCurrentOwnershipMatches() {
        TransactionImportProcessingAttempt processingAttempt = processingAttempt();

        when(transactionImportRepository.releaseProcessingLease(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                FENCING_TOKEN))
                .thenReturn(1);

        assertThat(leaseManager.release(processingAttempt)).isTrue();
    }

    @Test
    void releaseReturnsFalseWhenOwnershipHasChanged() {
        TransactionImportProcessingAttempt processingAttempt = processingAttempt();

        when(transactionImportRepository.releaseProcessingLease(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                FENCING_TOKEN))
                .thenReturn(0);

        assertThat(leaseManager.release(processingAttempt)).isFalse();
    }

    @Test
    void constructorRejectsLeaseDurationShorterThanOneSecond() {
        assertThatThrownBy(() ->
                new TransactionImportProcessingLeaseManager(transactionImportRepository,
                        Duration.ofMillis(500)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Processing lease duration must be at least one second");
    }

    @Test
    void acquireRejectsMissingEvent() {
        assertThatThrownBy(() -> leaseManager.acquire(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Transaction import requested event is required");

        verifyNoInteractions(transactionImportRepository, transactionImport);
    }

    @Test
    void renewRejectsMissingProcessingAttempt() {
        assertThatThrownBy(() -> leaseManager.renew(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Processing attempt is required");

        verifyNoInteractions(transactionImportRepository, transactionImport);
    }

    @Test
    void releaseRejectsMissingProcessingAttempt() {
        assertThatThrownBy(() -> leaseManager.release(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Processing attempt is required");

        verifyNoInteractions(transactionImportRepository, transactionImport);
    }

    private void matchingImportExistsForUpdate() {
        when(transactionImportRepository.findByIdAndAccountIdAndUserIdForUpdate(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID))
                .thenReturn(Optional.of(transactionImport));
    }

    private TransactionImportRequestedEvent event() {
        return TransactionImportRequestedEvent.create(EVENT_ID,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                "imports/9/import-uuid/source.csv",
                "correlation-123",
                Instant.parse("2026-08-14T11:59:00Z"));
    }

    private TransactionImportProcessingAttempt processingAttempt() {
        return new TransactionImportProcessingAttempt(EVENT_ID,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                FENCING_TOKEN);
    }

    @Test
    void acquireReturnsAbandonedWithoutChangingLease() {
        matchingImportExistsForUpdate();

        when(transactionImportRepository.getCurrentDatabaseTime()).thenReturn(CLAIMED_AT);
        when(transactionImport.getStatus()).thenReturn(TransactionImportStatus.ABANDONED);

        TransactionImportProcessingLeaseAcquisition acquisition = leaseManager.acquire(event());

        assertThat(acquisition.getOutcome())
                .isEqualTo(TransactionImportProcessingLeaseAcquisition.Outcome.ALREADY_ABANDONED);
        assertThat(acquisition.getProcessingAttempt()).isNull();
        assertThat(acquisition.isAcquired()).isFalse();

        verify(transactionImport, never()).hasActiveProcessingLease(CLAIMED_AT);
        verify(transactionImport, never()).claimProcessingLease(
                anyString(),
                eq(CLAIMED_AT),
                eq(LEASE_EXPIRES_AT)
        );
    }
}