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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 22L;
    private static final Long USER_ID = 9L;

    @Mock
    private TransactionImportRepository transactionImportRepository;

    @Mock
    private TransactionImport transactionImport;

    @InjectMocks
    private TransactionImportService transactionImportService;

    @Test
    void getRequestedImportReturnsMatchingImport() {
        when(transactionImportRepository.findByIdAndAccountIdAndUserId(IMPORT_ID, ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(transactionImport));

        TransactionImport result = transactionImportService.getRequestedImport(IMPORT_ID, ACCOUNT_ID, USER_ID);

        assertThat(result).isSameAs(transactionImport);

        verify(transactionImportRepository)
                .findByIdAndAccountIdAndUserId(IMPORT_ID, ACCOUNT_ID, USER_ID);
    }

    @Test
    void getRequestedImportThrowsWhenImportDoesNotMatchRequest() {
        when(transactionImportRepository.findByIdAndAccountIdAndUserId(IMPORT_ID, ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                transactionImportService.getRequestedImport(IMPORT_ID, ACCOUNT_ID, USER_ID)
        )
                .isInstanceOf(TransactionImportNotFoundException.class)
                .hasMessage(
                        "Transaction import 41 was not found for account 22 and user 9"
                );

        verify(transactionImportRepository)
                .findByIdAndAccountIdAndUserId(IMPORT_ID, ACCOUNT_ID, USER_ID);
    }
}