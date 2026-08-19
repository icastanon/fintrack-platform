package com.fintrack.workerservice.transactionimport.service;

import com.fintrack.workerservice.transactionimport.entity.TransactionImport;
import com.fintrack.workerservice.transactionimport.model.TransactionImportAbandonmentResult;
import com.fintrack.workerservice.transactionimport.repository.TransactionImportRejectedRowStagingRepository;
import com.fintrack.workerservice.transactionimport.repository.TransactionImportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportRetentionServiceTest {

    private static final Instant FAILED_BEFORE = Instant.parse("2026-07-19T12:00:00Z");
    private static final int BATCH_SIZE = 100;

    @Mock
    private TransactionImportRepository transactionImportRepository;

    @Mock
    private TransactionImportRejectedRowStagingRepository rejectedRowStagingRepository;

    @Mock
    private TransactionImport firstImport;

    @Mock
    private TransactionImport secondImport;

    @InjectMocks
    private TransactionImportRetentionService retentionService;

    @Test
    void abandonStaleFailedImportsMarksImportsAndDeletesTheirStaging() {
        when(transactionImportRepository.findStaleFailedImportsForUpdate(FAILED_BEFORE, BATCH_SIZE))
                .thenReturn(List.of(firstImport, secondImport));
        when(firstImport.getId()).thenReturn(41L);
        when(secondImport.getId()).thenReturn(42L);
        when(rejectedRowStagingRepository.deleteAllByImportIds(List.of(41L, 42L))).thenReturn(7);

        TransactionImportAbandonmentResult result =
                retentionService.abandonStaleFailedImports(FAILED_BEFORE, BATCH_SIZE);

        assertThat(result.getAbandonedImportCount()).isEqualTo(2);
        assertThat(result.getDeletedRejectedRowCount()).isEqualTo(7);

        InOrder order = inOrder(
                transactionImportRepository,
                firstImport,
                secondImport,
                rejectedRowStagingRepository
        );

        order.verify(transactionImportRepository)
                .findStaleFailedImportsForUpdate(FAILED_BEFORE, BATCH_SIZE);
        order.verify(firstImport).markAbandoned();
        order.verify(secondImport).markAbandoned();
        order.verify(transactionImportRepository).flush();
        order.verify(rejectedRowStagingRepository).deleteAllByImportIds(List.of(41L, 42L));
    }

    @Test
    void abandonStaleFailedImportsReturnsZeroWhenNoCandidatesExist() {
        when(transactionImportRepository.findStaleFailedImportsForUpdate(FAILED_BEFORE, BATCH_SIZE))
                .thenReturn(List.of());

        TransactionImportAbandonmentResult result =
                retentionService.abandonStaleFailedImports(FAILED_BEFORE, BATCH_SIZE);

        assertThat(result.getAbandonedImportCount()).isZero();
        assertThat(result.getDeletedRejectedRowCount()).isZero();

        verify(transactionImportRepository, never()).flush();
        verifyNoInteractions(rejectedRowStagingRepository);
    }

    @Test
    void abandonStaleFailedImportsRejectsMissingCutoff() {
        assertThatThrownBy(() -> retentionService.abandonStaleFailedImports(null, BATCH_SIZE))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Failed-before cutoff is required");

        verifyNoInteractions(transactionImportRepository, rejectedRowStagingRepository);
    }

    @Test
    void abandonStaleFailedImportsRejectsNonPositiveBatchSize() {
        assertThatThrownBy(() -> retentionService.abandonStaleFailedImports(FAILED_BEFORE, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Abandonment batch size must be positive");

        verifyNoInteractions(transactionImportRepository, rejectedRowStagingRepository);
    }
}