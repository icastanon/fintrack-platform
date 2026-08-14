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
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportStorageServiceTest {

    private static final String IMPORT_BUCKET = "fintrack-imports";
    private static final String SOURCE_OBJECT_KEY = "imports/9/abc/source.csv";
    private static final String REJECTED_OBJECT_KEY = "imports/9/abc/rejected.csv";
    private static final byte[] REJECTED_CONTENT =
            "row_number,raw_record,failure_reason\n".getBytes(StandardCharsets.UTF_8);

    @Mock
    private S3Client s3Client;

    @Mock
    private ResponseInputStream<GetObjectResponse> responseInputStream;

    private TransactionImportStorageService transactionImportStorageService;

    @BeforeEach
    void setUp() {
        transactionImportStorageService =
                new TransactionImportStorageService(s3Client, IMPORT_BUCKET);
    }

    @Test
    void openSourceReturnsStreamForRequestedObject() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseInputStream);

        InputStream result =
                transactionImportStorageService.openSource(SOURCE_OBJECT_KEY);

        assertThat(result).isSameAs(responseInputStream);

        ArgumentCaptor<GetObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(GetObjectRequest.class);

        verify(s3Client).getObject(requestCaptor.capture());

        assertThat(requestCaptor.getValue().bucket()).isEqualTo(IMPORT_BUCKET);
        assertThat(requestCaptor.getValue().key()).isEqualTo(SOURCE_OBJECT_KEY);
    }

    @Test
    void openSourceThrowsStorageExceptionWhenS3Fails() {
        AwsServiceException cause = S3Exception.builder()
                .message("S3 unavailable")
                .build();

        when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(cause);

        assertThatThrownBy(() ->
                transactionImportStorageService.openSource(SOURCE_OBJECT_KEY)
        )
                .isInstanceOf(TransactionImportStorageException.class)
                .hasMessage(
                        "Failed to open transaction import source object "
                                + SOURCE_OBJECT_KEY
                )
                .hasCause(cause);
    }

    @Test
    void uploadRejectedOutputUploadsContentToDeterministicObjectKey()
            throws Exception {
        when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).thenReturn(PutObjectResponse.builder().build());

        String result = transactionImportStorageService.uploadRejectedOutput(
                SOURCE_OBJECT_KEY,
                REJECTED_CONTENT
        );

        assertThat(result).isEqualTo(REJECTED_OBJECT_KEY);

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);

        ArgumentCaptor<RequestBody> bodyCaptor =
                ArgumentCaptor.forClass(RequestBody.class);

        verify(s3Client).putObject(
                requestCaptor.capture(),
                bodyCaptor.capture()
        );

        PutObjectRequest request = requestCaptor.getValue();

        assertThat(request.bucket()).isEqualTo(IMPORT_BUCKET);
        assertThat(request.key()).isEqualTo(REJECTED_OBJECT_KEY);
        assertThat(request.contentType()).isEqualTo("text/csv");

        byte[] uploadedContent = bodyCaptor.getValue()
                .contentStreamProvider()
                .newStream()
                .readAllBytes();

        assertThat(uploadedContent).isEqualTo(REJECTED_CONTENT);
    }

    @Test
    void uploadRejectedOutputThrowsStorageExceptionWhenS3Fails() {
        AwsServiceException cause = S3Exception.builder()
                .message("S3 unavailable")
                .build();

        when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).thenThrow(cause);

        assertThatThrownBy(() ->
                transactionImportStorageService.uploadRejectedOutput(
                        SOURCE_OBJECT_KEY,
                        REJECTED_CONTENT
                )
        )
                .isInstanceOf(TransactionImportStorageException.class)
                .hasMessage(
                        "Failed to upload transaction import rejected output "
                                + REJECTED_OBJECT_KEY
                )
                .hasCause(cause);
    }

    @Test
    void uploadRejectedOutputRejectsInvalidSourceObjectKey() {
        assertThatThrownBy(() ->
                transactionImportStorageService.uploadRejectedOutput(
                        "imports/9/abc/transactions.csv",
                        REJECTED_CONTENT
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Source object key must end with /source.csv");

        verifyNoInteractions(s3Client);
    }

    @Test
    void uploadRejectedOutputRejectsNullSourceObjectKey() {
        assertThatThrownBy(() ->
                transactionImportStorageService.uploadRejectedOutput(
                        null,
                        REJECTED_CONTENT
                )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Source object key is required");

        verifyNoInteractions(s3Client);
    }

    @Test
    void uploadRejectedOutputRejectsNullContent() {
        assertThatThrownBy(() ->
                transactionImportStorageService.uploadRejectedOutput(
                        SOURCE_OBJECT_KEY,
                        null
                )
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Rejected output content is required");

        verifyNoInteractions(s3Client);
    }
}