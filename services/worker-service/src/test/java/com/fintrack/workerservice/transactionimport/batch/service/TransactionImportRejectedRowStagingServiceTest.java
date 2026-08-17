package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.workerservice.transactionimport.entity.TransactionImportRejectedRowStaging;
import com.fintrack.workerservice.transactionimport.repository.TransactionImportRejectedRowStagingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportRejectedRowStagingServiceTest {

    private static final Long IMPORT_ID = 17L;
    private static final int ROW_NUMBER = 4;
    private static final String RAW_RECORD = "2026-08-10,EXPENSE,12.50,STARBUCKS,Coffee";
    private static final String FAILURE_REASON = "Row 4: amount must be a valid decimal number";
    private static final Instant COMPLETED_BEFORE = Instant.parse("2026-08-16T00:00:00Z");

    @Mock
    private TransactionImportRejectedRowStagingRepository rejectedRowStagingRepository;

    @InjectMocks
    private TransactionImportRejectedRowStagingService stagingService;

    @Test
    void stageReturnsTrueWhenRejectedRowIsInserted() {
        when(rejectedRowStagingRepository.insertIfAbsent(
                IMPORT_ID,
                ROW_NUMBER,
                RAW_RECORD,
                FAILURE_REASON
        )).thenReturn(1);

        boolean inserted = stagingService.stage(
                IMPORT_ID,
                ROW_NUMBER,
                RAW_RECORD,
                FAILURE_REASON
        );

        assertThat(inserted).isTrue();

        verify(rejectedRowStagingRepository).insertIfAbsent(
                IMPORT_ID,
                ROW_NUMBER,
                RAW_RECORD,
                FAILURE_REASON
        );
    }

    @Test
    void stageReturnsFalseWhenRejectedRowAlreadyExists() {
        when(rejectedRowStagingRepository.insertIfAbsent(
                IMPORT_ID,
                ROW_NUMBER,
                RAW_RECORD,
                FAILURE_REASON
        )).thenReturn(0);

        boolean inserted = stagingService.stage(
                IMPORT_ID,
                ROW_NUMBER,
                RAW_RECORD,
                FAILURE_REASON
        );

        assertThat(inserted).isFalse();
    }

    @Test
    void stageNormalizesFailureReasonBeforeInsertion() {
        when(rejectedRowStagingRepository.insertIfAbsent(
                IMPORT_ID,
                ROW_NUMBER,
                RAW_RECORD,
                FAILURE_REASON
        )).thenReturn(1);

        stagingService.stage(
                IMPORT_ID,
                ROW_NUMBER,
                RAW_RECORD,
                "  " + FAILURE_REASON + "  "
        );

        verify(rejectedRowStagingRepository).insertIfAbsent(
                IMPORT_ID,
                ROW_NUMBER,
                RAW_RECORD,
                FAILURE_REASON
        );
    }

    @Test
    void findAllReturnsRowsInRepositoryOrder() {
        TransactionImportRejectedRowStaging first =
                TransactionImportRejectedRowStaging.create(IMPORT_ID, 2, "first", "first failure");

        TransactionImportRejectedRowStaging second =
                TransactionImportRejectedRowStaging.create(IMPORT_ID, 5, "second", "second failure");

        when(rejectedRowStagingRepository.findAllByImportIdOrderByRowNumberAsc(IMPORT_ID))
                .thenReturn(List.of(first, second));

        List<TransactionImportRejectedRowStaging> result = stagingService.findAll(IMPORT_ID);

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void countReturnsNumberOfDurablyStagedRows() {
        when(rejectedRowStagingRepository.countByImportId(IMPORT_ID)).thenReturn(3L);

        long count = stagingService.count(IMPORT_ID);

        assertThat(count).isEqualTo(3);
    }

    @Test
    void deleteAllReturnsNumberOfDeletedRows() {
        when(rejectedRowStagingRepository.deleteAllByImportId(IMPORT_ID)).thenReturn(3);

        int deletedRows = stagingService.deleteAll(IMPORT_ID);

        assertThat(deletedRows).isEqualTo(3);
    }

    @Test
    void deleteAllForCompletedImportsBeforeReturnsNumberOfDeletedRows() {
        when(rejectedRowStagingRepository.deleteAllForCompletedImportsBefore(COMPLETED_BEFORE))
                .thenReturn(7);

        int deletedRows = stagingService.deleteAllForCompletedImportsBefore(COMPLETED_BEFORE);

        assertThat(deletedRows).isEqualTo(7);
        verify(rejectedRowStagingRepository).deleteAllForCompletedImportsBefore(COMPLETED_BEFORE);
    }

    @Test
    void findAllRejectsInvalidImportId() {
        assertThatThrownBy(() -> stagingService.findAll(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Import ID must be positive");

        verifyNoInteractions(rejectedRowStagingRepository);
    }

    @Test
    void countRejectsInvalidImportId() {
        assertThatThrownBy(() -> stagingService.count(0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Import ID must be positive");

        verifyNoInteractions(rejectedRowStagingRepository);
    }

    @Test
    void deleteAllRejectsInvalidImportId() {
        assertThatThrownBy(() -> stagingService.deleteAll(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Import ID must be positive");

        verifyNoInteractions(rejectedRowStagingRepository);
    }

    @Test
    void deleteAllForCompletedImportsBeforeRejectsMissingCutoff() {
        assertThatThrownBy(() -> stagingService.deleteAllForCompletedImportsBefore(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Completed-before cutoff is required");

        verifyNoInteractions(rejectedRowStagingRepository);
    }
}