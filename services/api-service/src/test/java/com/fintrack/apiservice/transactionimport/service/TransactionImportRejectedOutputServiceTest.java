package com.fintrack.apiservice.transactionimport.service;

import com.fintrack.apiservice.transactionimport.dto.TransactionImportRejectedOutput;
import com.fintrack.apiservice.transactionimport.entity.TransactionImport;
import com.fintrack.apiservice.transactionimport.exception.TransactionImportNotFoundException;
import com.fintrack.apiservice.transactionimport.exception.TransactionImportRejectedOutputNotAvailableException;
import com.fintrack.apiservice.transactionimport.exception.TransactionImportStorageException;
import com.fintrack.apiservice.transactionimport.repository.TransactionImportRepository;
import com.fintrack.apiservice.transactionimport.storage.TransactionImportStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportRejectedOutputServiceTest {

    private static final String REJECTED_OBJECT_KEY =
            "imports/7/import-123/rejected.csv";

    @Mock
    private TransactionImportRepository transactionImportRepository;

    @Mock
    private TransactionImportStorageService storageService;

    @InjectMocks
    private TransactionImportRejectedOutputService rejectedOutputService;

    @Test
    void getRejectedOutputReturnsOwnedImportRejectedCsv() {
        TransactionImport transactionImport = mock(TransactionImport.class);

        byte[] content = (
                "rowNumber,rejectionReason\n" +
                        "2,Amount must be positive"
        ).getBytes(StandardCharsets.UTF_8);

        when(transactionImportRepository.findByIdAndAccountUserId(41L, 7L))
                .thenReturn(Optional.of(transactionImport));
        when(transactionImport.getRejectedObjectKey()).thenReturn(REJECTED_OBJECT_KEY);
        when(transactionImport.getOriginalFileName()).thenReturn("august-transactions.csv");
        when(storageService.download(REJECTED_OBJECT_KEY)).thenReturn(content);

        TransactionImportRejectedOutput result =
                rejectedOutputService.getRejectedOutput(7L, 41L);

        assertThat(result.getFileName()).isEqualTo("august-transactions-rejected.csv");
        assertThat(result.getContent()).isEqualTo(content);

        verify(transactionImportRepository).findByIdAndAccountUserId(41L, 7L);
        verify(storageService).download(REJECTED_OBJECT_KEY);
    }

    @Test
    void getRejectedOutputHandlesUppercaseCsvExtension() {
        TransactionImport transactionImport = mock(TransactionImport.class);
        byte[] content = "rejected rows".getBytes(StandardCharsets.UTF_8);

        when(transactionImportRepository.findByIdAndAccountUserId(41L, 7L))
                .thenReturn(Optional.of(transactionImport));
        when(transactionImport.getRejectedObjectKey()).thenReturn(REJECTED_OBJECT_KEY);
        when(transactionImport.getOriginalFileName()).thenReturn("August-Transactions.CSV");
        when(storageService.download(REJECTED_OBJECT_KEY)).thenReturn(content);

        TransactionImportRejectedOutput result =
                rejectedOutputService.getRejectedOutput(7L, 41L);

        assertThat(result.getFileName()).isEqualTo("August-Transactions-rejected.csv");
        assertThat(result.getContent()).isEqualTo(content);
    }

    @Test
    void getRejectedOutputRejectsMissingOrUnownedImport() {
        when(transactionImportRepository.findByIdAndAccountUserId(41L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                rejectedOutputService.getRejectedOutput(7L, 41L)
        )
                .isInstanceOf(TransactionImportNotFoundException.class)
                .hasMessage("Transaction import was not found");

        verify(transactionImportRepository).findByIdAndAccountUserId(41L, 7L);
        verifyNoInteractions(storageService);
    }

    @Test
    void getRejectedOutputRejectsImportWithoutRejectedObject() {
        TransactionImport transactionImport = mock(TransactionImport.class);

        when(transactionImportRepository.findByIdAndAccountUserId(41L, 7L))
                .thenReturn(Optional.of(transactionImport));
        when(transactionImport.getRejectedObjectKey()).thenReturn(null);

        assertThatThrownBy(() ->
                rejectedOutputService.getRejectedOutput(7L, 41L)
        )
                .isInstanceOf(TransactionImportRejectedOutputNotAvailableException.class)
                .hasMessage("Rejected output is not available for this transaction import");

        verifyNoInteractions(storageService);
    }

    @Test
    void getRejectedOutputRejectsBlankRejectedObjectKey() {
        TransactionImport transactionImport = mock(TransactionImport.class);

        when(transactionImportRepository.findByIdAndAccountUserId(41L, 7L))
                .thenReturn(Optional.of(transactionImport));
        when(transactionImport.getRejectedObjectKey()).thenReturn("   ");

        assertThatThrownBy(() ->
                rejectedOutputService.getRejectedOutput(7L, 41L)
        )
                .isInstanceOf(TransactionImportRejectedOutputNotAvailableException.class)
                .hasMessage("Rejected output is not available for this transaction import");

        verifyNoInteractions(storageService);
    }

    @Test
    void getRejectedOutputPropagatesStorageFailure() {
        TransactionImport transactionImport = mock(TransactionImport.class);

        TransactionImportStorageException storageException =
                new TransactionImportStorageException(
                        "Failed to download the rejected transaction import file",
                        new IllegalStateException("S3 unavailable")
                );

        when(transactionImportRepository.findByIdAndAccountUserId(41L, 7L))
                .thenReturn(Optional.of(transactionImport));
        when(transactionImport.getRejectedObjectKey()).thenReturn(REJECTED_OBJECT_KEY);
        when(storageService.download(REJECTED_OBJECT_KEY)).thenThrow(storageException);

        assertThatThrownBy(() ->
                rejectedOutputService.getRejectedOutput(7L, 41L)
        ).isSameAs(storageException);

        verify(storageService).download(REJECTED_OBJECT_KEY);
    }
}