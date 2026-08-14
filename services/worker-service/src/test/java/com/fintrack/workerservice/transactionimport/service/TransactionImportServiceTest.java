package com.fintrack.workerservice.transactionimport.service;

import com.fintrack.workerservice.transactionimport.entity.TransactionImport;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportNotFoundException;
import com.fintrack.workerservice.transactionimport.repository.TransactionImportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 22L;
    private static final Long USER_ID = 9L;
    private static final String REJECTED_OBJECT_KEY = "imports/9/import-uuid/rejected.csv";

    @Mock
    private TransactionImportRepository transactionImportRepository;

    @Mock
    private TransactionImport transactionImport;

    @InjectMocks
    private TransactionImportService transactionImportService;

    @Test
    void getRequestedImportReturnsMatchingImport() {
        matchingImportExists();

        TransactionImport result =
                transactionImportService.getRequestedImport(IMPORT_ID, ACCOUNT_ID, USER_ID);

        assertThat(result).isSameAs(transactionImport);

        verify(transactionImportRepository)
                .findByIdAndAccountIdAndUserId(IMPORT_ID, ACCOUNT_ID, USER_ID);
    }

    @Test
    void getRequestedImportThrowsWhenImportDoesNotMatchRequest() {
        matchingImportDoesNotExist();

        assertThatThrownBy(() ->
                transactionImportService.getRequestedImport(IMPORT_ID, ACCOUNT_ID, USER_ID)
        )
                .isInstanceOf(TransactionImportNotFoundException.class)
                .hasMessage("Transaction import 41 was not found for account 22 and user 9");
    }

    @Test
    void markRunningLoadsOwnedImportAndTransitionsIt() {
        matchingImportExists();

        transactionImportService.markRunning(IMPORT_ID, ACCOUNT_ID, USER_ID);

        verify(transactionImport).markRunning();
    }

    @Test
    void markRunningThrowsWhenImportDoesNotMatchRequest() {
        matchingImportDoesNotExist();

        assertThatThrownBy(() ->
                transactionImportService.markRunning(IMPORT_ID, ACCOUNT_ID, USER_ID)
        )
                .isInstanceOf(TransactionImportNotFoundException.class)
                .hasMessage("Transaction import 41 was not found for account 22 and user 9");

        verifyNoInteractions(transactionImport);
    }

    @Test
    void markCompletedLoadsOwnedImportAndStoresFinalState() {
        matchingImportExists();

        transactionImportService.markCompleted(
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                8,
                2,
                0,
                REJECTED_OBJECT_KEY
        );

        verify(transactionImport).markCompleted(8, 2, 0, REJECTED_OBJECT_KEY);
    }

    @Test
    void markCompletedWithoutRejectedRowsStoresNullObjectKey() {
        matchingImportExists();

        transactionImportService.markCompleted(
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                8,
                0,
                0,
                null
        );

        verify(transactionImport).markCompleted(8, 0, 0, null);
    }

    @Test
    void markCompletedThrowsWhenImportDoesNotMatchRequest() {
        matchingImportDoesNotExist();

        assertThatThrownBy(() ->
                transactionImportService.markCompleted(
                        IMPORT_ID,
                        ACCOUNT_ID,
                        USER_ID,
                        8,
                        0,
                        0,
                        null
                )
        )
                .isInstanceOf(TransactionImportNotFoundException.class)
                .hasMessage("Transaction import 41 was not found for account 22 and user 9");

        verifyNoInteractions(transactionImport);
    }

    @Test
    void markFailedLoadsOwnedImportAndStoresPartialCounters() {
        matchingImportExists();

        transactionImportService.markFailed(
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                4,
                1,
                0,
                "Temporary failure"
        );

        verify(transactionImport).markFailed(4, 1, 0, "Temporary failure");
    }

    private void matchingImportExists() {
        when(transactionImportRepository.findByIdAndAccountIdAndUserId(
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID
        )).thenReturn(Optional.of(transactionImport));
    }

    private void matchingImportDoesNotExist() {
        when(transactionImportRepository.findByIdAndAccountIdAndUserId(
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID
        )).thenReturn(Optional.empty());
    }
}