package com.fintrack.workerservice.transactionimport.storage;

import com.fintrack.workerservice.transactionimport.exception.TransactionImportStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportStorageServiceTest {

    private static final String IMPORT_BUCKET = "fintrack-imports";
    private static final String OBJECT_KEY = "imports/9/abc/source.csv";

    @Mock
    private S3Client s3Client;

    @Mock
    private ResponseInputStream<GetObjectResponse> responseInputStream;

    private TransactionImportStorageService transactionImportStorageService;

    @BeforeEach
    void setUp() {
        transactionImportStorageService = new TransactionImportStorageService(s3Client, IMPORT_BUCKET);
    }

    @Test
    void openSourceReturnsStreamForRequestedObject() {
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);

        InputStream result = transactionImportStorageService.openSource(OBJECT_KEY);

        assertThat(result).isSameAs(responseInputStream);

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(requestCaptor.capture());

        assertThat(requestCaptor.getValue().bucket()).isEqualTo(IMPORT_BUCKET);
        assertThat(requestCaptor.getValue().key()).isEqualTo(OBJECT_KEY);
    }

    @Test
    void openSourceThrowsStorageExceptionWhenS3Fails() {
        AwsServiceException cause = S3Exception.builder()
                .message("S3 unavailable")
                .build();

        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(cause);

        assertThatThrownBy(() -> transactionImportStorageService.openSource(OBJECT_KEY))
                .isInstanceOf(TransactionImportStorageException.class)
                .hasMessage("Failed to open transaction import source object " + OBJECT_KEY)
                .hasCause(cause);
    }
}