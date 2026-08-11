package com.fintrack.apiservice.transactionimport.storage;

import com.fintrack.apiservice.transactionimport.exception.TransactionImportStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private TransactionImportStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new TransactionImportStorageService(s3Client, "fintrack-imports");
    }

    @Test
    void uploadStoresCsvUsingGeneratedPrivateObjectKey() throws Exception {
        byte[] content = "transactionDate,type,amount\n2026-08-10,EXPENSE,25.00"
                .getBytes(StandardCharsets.UTF_8);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().eTag("test-etag").build());

        String objectKey = storageService.upload(
                7L,
                new ByteArrayInputStream(content),
                content.length,
                "text/csv"
        );

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> requestBodyCaptor = ArgumentCaptor.forClass(RequestBody.class);

        verify(s3Client).putObject(requestCaptor.capture(), requestBodyCaptor.capture());

        PutObjectRequest request = requestCaptor.getValue();
        RequestBody requestBody = requestBodyCaptor.getValue();

        assertThat(objectKey).matches("imports/7/[0-9a-f\\-]{36}/source\\.csv");
        assertThat(request.bucket()).isEqualTo("fintrack-imports");
        assertThat(request.key()).isEqualTo(objectKey);
        assertThat(request.contentLength()).isEqualTo((long) content.length);
        assertThat(request.contentType()).isEqualTo("text/csv");
        assertThat(requestBody.contentLength()).isEqualTo(content.length);
        assertThat(requestBody.contentStreamProvider().newStream().readAllBytes()).isEqualTo(content);
    }

    @Test
    void uploadTranslatesSdkFailureIntoStorageException() {
        byte[] content = "transactionDate,type,amount".getBytes(StandardCharsets.UTF_8);

        SdkClientException sdkException = SdkClientException.builder()
                .message("S3 unavailable")
                .build();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(sdkException);

        assertThatThrownBy(() ->
                storageService.upload(
                        7L,
                        new ByteArrayInputStream(content),
                        content.length,
                        "text/csv"
                )
        )
                .isInstanceOf(TransactionImportStorageException.class)
                .hasMessage("Failed to upload the transaction import file")
                .hasCause(sdkException);
    }

    @Test
    void downloadReturnsRejectedCsvFromImportBucket() {
        String objectKey = "imports/7/test-id/rejected.csv";

        byte[] rejectedContent = (
                "rowNumber,rejectionReason\n" +
                        "2,Amount must be positive"
        ).getBytes(StandardCharsets.UTF_8);

        GetObjectResponse getObjectResponse = GetObjectResponse.builder()
                .contentLength((long) rejectedContent.length)
                .contentType("text/csv")
                .build();

        ResponseBytes<GetObjectResponse> responseBytes =
                ResponseBytes.fromByteArray(getObjectResponse, rejectedContent);

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(responseBytes);

        byte[] result = storageService.download(objectKey);

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);

        verify(s3Client).getObjectAsBytes(requestCaptor.capture());

        GetObjectRequest request = requestCaptor.getValue();

        assertThat(request.bucket()).isEqualTo("fintrack-imports");
        assertThat(request.key()).isEqualTo(objectKey);
        assertThat(result).isEqualTo(rejectedContent);
    }

    @Test
    void downloadTranslatesSdkFailureIntoStorageException() {
        String objectKey = "imports/7/test-id/rejected.csv";

        SdkClientException sdkException = SdkClientException.builder()
                .message("S3 unavailable")
                .build();

        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(sdkException);

        assertThatThrownBy(() -> storageService.download(objectKey))
                .isInstanceOf(TransactionImportStorageException.class)
                .hasMessage("Failed to download the rejected transaction import file")
                .hasCause(sdkException);
    }

    @Test
    void deleteQuietlyDeletesObjectFromImportBucket() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());

        storageService.deleteQuietly("imports/7/test-id/source.csv");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);

        verify(s3Client).deleteObject(requestCaptor.capture());

        DeleteObjectRequest request = requestCaptor.getValue();

        assertThat(request.bucket()).isEqualTo("fintrack-imports");
        assertThat(request.key()).isEqualTo("imports/7/test-id/source.csv");
    }

    @Test
    void deleteQuietlyDoesNotPropagateSdkFailure() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(
                        SdkClientException.builder()
                                .message("S3 unavailable")
                                .build()
                );

        assertThatCode(() ->
                storageService.deleteQuietly("imports/7/test-id/source.csv")
        ).doesNotThrowAnyException();

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteQuietlyIgnoresMissingObjectKey() {
        storageService.deleteQuietly(null);
        storageService.deleteQuietly("   ");

        verifyNoInteractions(s3Client);
    }
}