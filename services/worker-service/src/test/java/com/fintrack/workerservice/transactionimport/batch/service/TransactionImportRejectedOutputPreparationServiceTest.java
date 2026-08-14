package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportRejectedOutput;
import com.fintrack.workerservice.transactionimport.entity.TransactionImportRejectedRowStaging;
import com.fintrack.workerservice.transactionimport.storage.TransactionImportStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportRejectedOutputPreparationServiceTest {

    private static final Long IMPORT_ID = 41L;
    private static final String SOURCE_OBJECT_KEY = "imports/9/test/source.csv";
    private static final String REJECTED_OBJECT_KEY = "imports/9/test/rejected.csv";

    @Mock
    private TransactionImportRejectedRowStagingService rejectedRowStagingService;

    @Mock
    private TransactionImportRejectedCsvBuilder rejectedCsvBuilder;

    @Mock
    private TransactionImportStorageService storageService;

    @Mock
    private TransactionImportRejectedRowStaging firstRejectedRow;

    @Mock
    private TransactionImportRejectedRowStaging secondRejectedRow;

    @InjectMocks
    private TransactionImportRejectedOutputPreparationService preparationService;

    @Test
    void prepareAndUploadReturnsNoneWithoutBuildingOrUploadingWhenNoRejectedRowsExist() {
        when(rejectedRowStagingService.findAll(IMPORT_ID)).thenReturn(List.of());

        TransactionImportRejectedOutput output =
                preparationService.prepareAndUpload(IMPORT_ID, SOURCE_OBJECT_KEY);

        assertThat(output.exists()).isFalse();
        assertThat(output.getRejectedRowCount()).isZero();
        assertThat(output.getObjectKey()).isNull();

        verify(rejectedRowStagingService).findAll(IMPORT_ID);
        verifyNoInteractions(rejectedCsvBuilder, storageService);
    }

    @Test
    void prepareAndUploadBuildsAndUploadsRejectedOutput() {
        List<TransactionImportRejectedRowStaging> rejectedRows =
                List.of(firstRejectedRow, secondRejectedRow);

        byte[] rejectedCsv = "rejected csv".getBytes(StandardCharsets.UTF_8);

        when(rejectedRowStagingService.findAll(IMPORT_ID)).thenReturn(rejectedRows);
        when(rejectedCsvBuilder.build(rejectedRows)).thenReturn(rejectedCsv);
        when(storageService.uploadRejectedOutput(SOURCE_OBJECT_KEY, rejectedCsv))
                .thenReturn(REJECTED_OBJECT_KEY);

        TransactionImportRejectedOutput output =
                preparationService.prepareAndUpload(IMPORT_ID, SOURCE_OBJECT_KEY);

        assertThat(output.exists()).isTrue();
        assertThat(output.getRejectedRowCount()).isEqualTo(2);
        assertThat(output.getObjectKey()).isEqualTo(REJECTED_OBJECT_KEY);

        InOrder order = inOrder(rejectedRowStagingService, rejectedCsvBuilder, storageService);

        order.verify(rejectedRowStagingService).findAll(IMPORT_ID);
        order.verify(rejectedCsvBuilder).build(rejectedRows);
        order.verify(storageService).uploadRejectedOutput(SOURCE_OBJECT_KEY, rejectedCsv);
    }

    @Test
    void prepareAndUploadRejectsNullSourceObjectKeyBeforeUsingDependencies() {
        assertThatThrownBy(() -> preparationService.prepareAndUpload(IMPORT_ID, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Source object key is required");

        verifyNoInteractions(rejectedRowStagingService, rejectedCsvBuilder, storageService);
    }
}