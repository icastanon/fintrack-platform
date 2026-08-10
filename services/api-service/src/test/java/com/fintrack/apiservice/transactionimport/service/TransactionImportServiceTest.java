package com.fintrack.apiservice.transactionimport.service;

import com.fintrack.apiservice.transactionimport.dto.TransactionImportResponse;
import com.fintrack.apiservice.transactionimport.entity.TransactionImport;
import com.fintrack.apiservice.transactionimport.exception.TransactionImportNotFoundException;
import com.fintrack.apiservice.transactionimport.mapper.TransactionImportMapper;
import com.fintrack.apiservice.transactionimport.repository.TransactionImportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

    @Mock
    private TransactionImportRepository transactionImportRepository;

    @Mock
    private TransactionImportMapper transactionImportMapper;

    @InjectMocks
    private TransactionImportService transactionImportService;

    @Test
    void getImportReturnsOwnedImport() {
        TransactionImport transactionImport = mock(TransactionImport.class);
        TransactionImportResponse expectedResponse = mock(TransactionImportResponse.class);

        when(transactionImportRepository.findByIdAndAccountUserId(41L, 7L))
                .thenReturn(Optional.of(transactionImport));
        when(transactionImportMapper.toResponse(transactionImport)).thenReturn(expectedResponse);

        TransactionImportResponse result = transactionImportService.getImport(7L, 41L);

        assertThat(result).isSameAs(expectedResponse);

        verify(transactionImportRepository).findByIdAndAccountUserId(41L, 7L);
        verify(transactionImportMapper).toResponse(transactionImport);
    }

    @Test
    void getImportRejectsMissingOrUnownedImport() {
        when(transactionImportRepository.findByIdAndAccountUserId(41L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionImportService.getImport(7L, 41L))
                .isInstanceOf(TransactionImportNotFoundException.class)
                .hasMessage("Transaction import was not found");

        verify(transactionImportRepository).findByIdAndAccountUserId(41L, 7L);
        verifyNoInteractions(transactionImportMapper);
    }
}