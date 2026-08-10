package com.fintrack.apiservice.transactionimport.service;

import com.fintrack.apiservice.account.entity.AccountStatus;
import com.fintrack.apiservice.account.entity.FinancialAccount;
import com.fintrack.apiservice.account.exception.FinancialAccountClosedException;
import com.fintrack.apiservice.account.exception.FinancialAccountNotFoundException;
import com.fintrack.apiservice.account.repository.FinancialAccountRepository;
import com.fintrack.apiservice.transactionimport.dto.TransactionImportResponse;
import com.fintrack.apiservice.transactionimport.entity.TransactionImport;
import com.fintrack.apiservice.transactionimport.exception.InvalidTransactionImportFileException;
import com.fintrack.apiservice.transactionimport.exception.TransactionImportStorageException;
import com.fintrack.apiservice.transactionimport.mapper.TransactionImportMapper;
import com.fintrack.apiservice.transactionimport.storage.TransactionImportStorageService;
import com.fintrack.apiservice.transactionimport.validation.TransactionImportFileValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportSubmissionServiceTest {

    private static final String ORIGINAL_FILE_NAME = "august-transactions.csv";
    private static final String CONTENT_TYPE = "text/csv";
    private static final String SOURCE_OBJECT_KEY = "imports/7/import-123/source.csv";

    private static final byte[] CSV_CONTENT = (
            "transactionDate,type,amount\n" +
                    "2026-08-10,EXPENSE,25.00"
    ).getBytes(StandardCharsets.UTF_8);

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private TransactionImportFileValidator fileValidator;

    @Mock
    private TransactionImportStorageService storageService;

    @Mock
    private TransactionImportPersistenceService persistenceService;

    @Mock
    private TransactionImportMapper transactionImportMapper;

    @InjectMocks
    private TransactionImportSubmissionService submissionService;

    @Test
    void submitValidatesUploadsPersistsAndMapsImport() {
        MockMultipartFile file = createFile();
        FinancialAccount account = createAccount(AccountStatus.ACTIVE);
        TransactionImport transactionImport = mock(TransactionImport.class);
        TransactionImportResponse expectedResponse = mock(TransactionImportResponse.class);

        when(fileValidator.validate(file)).thenReturn(ORIGINAL_FILE_NAME);
        when(financialAccountRepository.findByIdAndUserId(15L, 7L))
                .thenReturn(Optional.of(account));

        when(storageService.upload(
                eq(7L),
                any(InputStream.class),
                eq((long) CSV_CONTENT.length),
                eq(CONTENT_TYPE)
        )).thenReturn(SOURCE_OBJECT_KEY);

        when(persistenceService.createQueuedImport(
                7L,
                15L,
                ORIGINAL_FILE_NAME,
                CONTENT_TYPE,
                CSV_CONTENT.length,
                SOURCE_OBJECT_KEY
        )).thenReturn(transactionImport);

        when(transactionImportMapper.toResponse(transactionImport)).thenReturn(expectedResponse);

        TransactionImportResponse result = submissionService.submit(7L, 15L, file);

        assertThat(result).isSameAs(expectedResponse);

        InOrder inOrder = inOrder(
                fileValidator,
                financialAccountRepository,
                storageService,
                persistenceService,
                transactionImportMapper
        );

        inOrder.verify(fileValidator).validate(file);
        inOrder.verify(financialAccountRepository).findByIdAndUserId(15L, 7L);

        inOrder.verify(storageService).upload(
                eq(7L),
                any(InputStream.class),
                eq((long) CSV_CONTENT.length),
                eq(CONTENT_TYPE)
        );

        inOrder.verify(persistenceService).createQueuedImport(
                7L,
                15L,
                ORIGINAL_FILE_NAME,
                CONTENT_TYPE,
                CSV_CONTENT.length,
                SOURCE_OBJECT_KEY
        );

        inOrder.verify(transactionImportMapper).toResponse(transactionImport);

        verify(storageService, never()).deleteQuietly(anyString());
    }

    @Test
    void submitWhenValidationFailsDoesNotCheckOwnershipOrUpload() {
        MockMultipartFile file = createFile();

        InvalidTransactionImportFileException exception =
                new InvalidTransactionImportFileException("Invalid CSV");

        when(fileValidator.validate(file)).thenThrow(exception);

        assertThatThrownBy(() -> submissionService.submit(7L, 15L, file))
                .isSameAs(exception);

        verifyNoInteractions(
                financialAccountRepository,
                storageService,
                persistenceService,
                transactionImportMapper
        );
    }

    @Test
    void submitRejectsMissingOrUnownedAccountBeforeUpload() {
        MockMultipartFile file = createFile();

        when(fileValidator.validate(file)).thenReturn(ORIGINAL_FILE_NAME);
        when(financialAccountRepository.findByIdAndUserId(15L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> submissionService.submit(7L, 15L, file))
                .isInstanceOf(FinancialAccountNotFoundException.class)
                .hasMessage("Financial account not found");

        verifyNoInteractions(storageService, persistenceService, transactionImportMapper);
    }

    @Test
    void submitRejectsClosedAccountBeforeUpload() {
        MockMultipartFile file = createFile();
        FinancialAccount account = createAccount(AccountStatus.CLOSED);

        when(fileValidator.validate(file)).thenReturn(ORIGINAL_FILE_NAME);
        when(financialAccountRepository.findByIdAndUserId(15L, 7L))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> submissionService.submit(7L, 15L, file))
                .isInstanceOf(FinancialAccountClosedException.class)
                .hasMessage("Closed financial accounts cannot be modified");

        verifyNoInteractions(storageService, persistenceService, transactionImportMapper);
    }

    @Test
    void submitWhenFileCannotBeReadDoesNotUploadOrPersist() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        FinancialAccount account = createAccount(AccountStatus.ACTIVE);
        IOException ioException = new IOException("Failed to open upload stream");

        when(fileValidator.validate(file)).thenReturn(ORIGINAL_FILE_NAME);
        when(financialAccountRepository.findByIdAndUserId(15L, 7L))
                .thenReturn(Optional.of(account));
        when(file.getContentType()).thenReturn(CONTENT_TYPE);
        when(file.getInputStream()).thenThrow(ioException);

        assertThatThrownBy(() -> submissionService.submit(7L, 15L, file))
                .isInstanceOf(TransactionImportStorageException.class)
                .hasMessage("Failed to read the transaction import file")
                .hasCause(ioException);

        verifyNoInteractions(storageService, persistenceService, transactionImportMapper);
    }

    @Test
    void submitWhenS3UploadFailsDoesNotPersistOrAttemptCleanup() {
        MockMultipartFile file = createFile();
        FinancialAccount account = createAccount(AccountStatus.ACTIVE);

        TransactionImportStorageException storageException =
                new TransactionImportStorageException(
                        "Failed to upload the transaction import file",
                        new IllegalStateException("S3 unavailable")
                );

        when(fileValidator.validate(file)).thenReturn(ORIGINAL_FILE_NAME);
        when(financialAccountRepository.findByIdAndUserId(15L, 7L))
                .thenReturn(Optional.of(account));

        when(storageService.upload(
                eq(7L),
                any(InputStream.class),
                eq((long) CSV_CONTENT.length),
                eq(CONTENT_TYPE)
        )).thenThrow(storageException);

        assertThatThrownBy(() -> submissionService.submit(7L, 15L, file))
                .isSameAs(storageException);

        verify(storageService, never()).deleteQuietly(anyString());
        verifyNoInteractions(persistenceService, transactionImportMapper);
    }

    @Test
    void submitWhenPersistenceFailsDeletesUploadedObjectAndPropagatesFailure() {
        MockMultipartFile file = createFile();
        FinancialAccount account = createAccount(AccountStatus.ACTIVE);
        IllegalStateException persistenceException = new IllegalStateException("Database write failed");

        when(fileValidator.validate(file)).thenReturn(ORIGINAL_FILE_NAME);
        when(financialAccountRepository.findByIdAndUserId(15L, 7L))
                .thenReturn(Optional.of(account));

        when(storageService.upload(
                eq(7L),
                any(InputStream.class),
                eq((long) CSV_CONTENT.length),
                eq(CONTENT_TYPE)
        )).thenReturn(SOURCE_OBJECT_KEY);

        when(persistenceService.createQueuedImport(
                7L,
                15L,
                ORIGINAL_FILE_NAME,
                CONTENT_TYPE,
                CSV_CONTENT.length,
                SOURCE_OBJECT_KEY
        )).thenThrow(persistenceException);

        assertThatThrownBy(() -> submissionService.submit(7L, 15L, file))
                .isSameAs(persistenceException);

        verify(storageService).deleteQuietly(SOURCE_OBJECT_KEY);
        verifyNoInteractions(transactionImportMapper);
    }

    @Test
    void submitWhenResponseMappingFailsDoesNotDeleteCommittedImportObject() {
        MockMultipartFile file = createFile();
        FinancialAccount account = createAccount(AccountStatus.ACTIVE);
        TransactionImport transactionImport = mock(TransactionImport.class);
        IllegalStateException mappingException = new IllegalStateException("Mapping failed");

        when(fileValidator.validate(file)).thenReturn(ORIGINAL_FILE_NAME);
        when(financialAccountRepository.findByIdAndUserId(15L, 7L))
                .thenReturn(Optional.of(account));

        when(storageService.upload(
                eq(7L),
                any(InputStream.class),
                eq((long) CSV_CONTENT.length),
                eq(CONTENT_TYPE)
        )).thenReturn(SOURCE_OBJECT_KEY);

        when(persistenceService.createQueuedImport(
                7L,
                15L,
                ORIGINAL_FILE_NAME,
                CONTENT_TYPE,
                CSV_CONTENT.length,
                SOURCE_OBJECT_KEY
        )).thenReturn(transactionImport);

        when(transactionImportMapper.toResponse(transactionImport)).thenThrow(mappingException);

        assertThatThrownBy(() -> submissionService.submit(7L, 15L, file))
                .isSameAs(mappingException);

        verify(storageService, never()).deleteQuietly(anyString());
    }

    private MockMultipartFile createFile() {
        return new MockMultipartFile(
                "file",
                ORIGINAL_FILE_NAME,
                CONTENT_TYPE,
                CSV_CONTENT
        );
    }

    private FinancialAccount createAccount(AccountStatus status) {
        FinancialAccount account = new FinancialAccount();
        account.setId(15L);
        account.setStatus(status);
        return account;
    }
}