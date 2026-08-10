package com.fintrack.apiservice.transactionimport.service;

import com.fintrack.apiservice.account.entity.AccountStatus;
import com.fintrack.apiservice.account.entity.FinancialAccount;
import com.fintrack.apiservice.account.exception.FinancialAccountClosedException;
import com.fintrack.apiservice.account.exception.FinancialAccountNotFoundException;
import com.fintrack.apiservice.account.repository.FinancialAccountRepository;
import com.fintrack.apiservice.outbox.service.OutboxEventWriter;
import com.fintrack.apiservice.transactionimport.entity.TransactionImport;
import com.fintrack.apiservice.transactionimport.entity.TransactionImportStatus;
import com.fintrack.apiservice.transactionimport.repository.TransactionImportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportPersistenceServiceTest {

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private TransactionImportRepository transactionImportRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @Captor
    private ArgumentCaptor<TransactionImport> transactionImportCaptor;

    @InjectMocks
    private TransactionImportPersistenceService transactionImportPersistenceService;

    @Test
    void createQueuedImportPersistsImportAndWritesOutboxEvent() {
        FinancialAccount account = createAccount(AccountStatus.ACTIVE);

        when(financialAccountRepository.findByIdAndUserId(15L, 7L))
                .thenReturn(Optional.of(account));

        when(transactionImportRepository.saveAndFlush(any(TransactionImport.class)))
                .thenAnswer(invocation -> {
                    TransactionImport transactionImport = invocation.getArgument(0);
                    ReflectionTestUtils.setField(transactionImport, "id", 41L);
                    return transactionImport;
                });

        TransactionImport result = transactionImportPersistenceService.createQueuedImport(
                7L,
                15L,
                "august-transactions.csv",
                "text/csv",
                2048L,
                "imports/7/import-123/source.csv"
        );

        verify(transactionImportRepository).saveAndFlush(transactionImportCaptor.capture());

        TransactionImport savedImport = transactionImportCaptor.getValue();

        assertThat(result).isSameAs(savedImport);
        assertThat(savedImport.getId()).isEqualTo(41L);
        assertThat(savedImport.getAccount()).isSameAs(account);
        assertThat(savedImport.getOriginalFileName()).isEqualTo("august-transactions.csv");
        assertThat(savedImport.getContentType()).isEqualTo("text/csv");
        assertThat(savedImport.getFileSizeBytes()).isEqualTo(2048L);
        assertThat(savedImport.getSourceObjectKey()).isEqualTo("imports/7/import-123/source.csv");
        assertThat(savedImport.getRejectedObjectKey()).isNull();
        assertThat(savedImport.getStatus()).isEqualTo(TransactionImportStatus.QUEUED);
        assertThat(savedImport.getTotalRows()).isNull();
        assertThat(savedImport.getProcessedRows()).isZero();
        assertThat(savedImport.getSuccessfulRows()).isZero();
        assertThat(savedImport.getSkippedRows()).isZero();
        assertThat(savedImport.getFailedRows()).isZero();
        assertThat(savedImport.getFailureSummary()).isNull();
        assertThat(savedImport.getStartedAt()).isNull();
        assertThat(savedImport.getCompletedAt()).isNull();

        verify(outboxEventWriter).writeTransactionImportRequested(
                41L,
                15L,
                7L,
                "imports/7/import-123/source.csv"
        );
    }

    @Test
    void createQueuedImportRejectsMissingOrUnownedAccount() {
        when(financialAccountRepository.findByIdAndUserId(15L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                transactionImportPersistenceService.createQueuedImport(
                        7L,
                        15L,
                        "august-transactions.csv",
                        "text/csv",
                        2048L,
                        "imports/7/import-123/source.csv"
                )
        )
                .isInstanceOf(FinancialAccountNotFoundException.class)
                .hasMessage("Financial account not found");

        verify(financialAccountRepository).findByIdAndUserId(15L, 7L);
        verifyNoInteractions(transactionImportRepository, outboxEventWriter);
    }

    @Test
    void createQueuedImportRejectsClosedAccount() {
        FinancialAccount account = createAccount(AccountStatus.CLOSED);

        when(financialAccountRepository.findByIdAndUserId(15L, 7L))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() ->
                transactionImportPersistenceService.createQueuedImport(
                        7L,
                        15L,
                        "august-transactions.csv",
                        "text/csv",
                        2048L,
                        "imports/7/import-123/source.csv"
                )
        )
                .isInstanceOf(FinancialAccountClosedException.class)
                .hasMessage("Closed financial accounts cannot be modified");

        verify(financialAccountRepository).findByIdAndUserId(15L, 7L);
        verifyNoInteractions(transactionImportRepository, outboxEventWriter);
    }

    @Test
    void createQueuedImportWhenImportPersistenceFailsDoesNotWriteOutboxEvent() {
        FinancialAccount account = createAccount(AccountStatus.ACTIVE);

        when(financialAccountRepository.findByIdAndUserId(15L, 7L))
                .thenReturn(Optional.of(account));

        when(transactionImportRepository.saveAndFlush(any(TransactionImport.class)))
                .thenThrow(new DataIntegrityViolationException("Import persistence failed"));

        assertThatThrownBy(() ->
                transactionImportPersistenceService.createQueuedImport(
                        7L,
                        15L,
                        "august-transactions.csv",
                        "text/csv",
                        2048L,
                        "imports/7/import-123/source.csv"
                )
        )
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("Import persistence failed");

        verify(transactionImportRepository).saveAndFlush(any(TransactionImport.class));
        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    void createQueuedImportPropagatesOutboxFailure() {
        FinancialAccount account = createAccount(AccountStatus.ACTIVE);

        when(financialAccountRepository.findByIdAndUserId(15L, 7L))
                .thenReturn(Optional.of(account));

        when(transactionImportRepository.saveAndFlush(any(TransactionImport.class)))
                .thenAnswer(invocation -> {
                    TransactionImport transactionImport = invocation.getArgument(0);
                    ReflectionTestUtils.setField(transactionImport, "id", 41L);
                    return transactionImport;
                });

        org.mockito.Mockito.doThrow(new IllegalStateException("Outbox write failed"))
                .when(outboxEventWriter)
                .writeTransactionImportRequested(
                        41L,
                        15L,
                        7L,
                        "imports/7/import-123/source.csv"
                );

        assertThatThrownBy(() ->
                transactionImportPersistenceService.createQueuedImport(
                        7L,
                        15L,
                        "august-transactions.csv",
                        "text/csv",
                        2048L,
                        "imports/7/import-123/source.csv"
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Outbox write failed");

        verify(transactionImportRepository).saveAndFlush(any(TransactionImport.class));
        verify(outboxEventWriter).writeTransactionImportRequested(
                41L,
                15L,
                7L,
                "imports/7/import-123/source.csv"
        );
    }

    private FinancialAccount createAccount(AccountStatus status) {
        FinancialAccount account = new FinancialAccount();
        account.setId(15L);
        account.setStatus(status);
        return account;
    }
}