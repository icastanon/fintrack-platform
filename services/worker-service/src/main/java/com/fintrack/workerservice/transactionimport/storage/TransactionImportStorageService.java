package com.fintrack.workerservice.transactionimport.storage;

import com.fintrack.workerservice.transactionimport.exception.TransactionImportStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.util.Objects;

@Service
public class TransactionImportStorageService {

    private static final String SOURCE_FILE_NAME = "source.csv";
    private static final String REJECTED_FILE_NAME = "rejected.csv";
    private static final String CSV_CONTENT_TYPE = "text/csv";

    private final S3Client s3Client;
    private final String importBucket;

    public TransactionImportStorageService(S3Client s3Client,
                                           @Value("${fintrack.s3.import-bucket}") String importBucket) {
        this.s3Client = s3Client;
        this.importBucket = importBucket;
    }

    public InputStream openSource(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(importBucket)
                .key(objectKey)
                .build();

        try {
            return s3Client.getObject(request);
        } catch (SdkException exception) {
            throw new TransactionImportStorageException(
                    "Failed to open transaction import source object " + objectKey,
                    exception
            );
        }
    }

    public String uploadRejectedOutput(String sourceObjectKey, byte[] content) {
        Objects.requireNonNull(content, "Rejected output content is required");

        String rejectedObjectKey = buildRejectedObjectKey(sourceObjectKey);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(importBucket)
                .key(rejectedObjectKey)
                .contentType(CSV_CONTENT_TYPE)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(content));
            return rejectedObjectKey;
        } catch (SdkException exception) {
            throw new TransactionImportStorageException(
                    "Failed to upload transaction import rejected output "
                            + rejectedObjectKey,
                    exception
            );
        }
    }

    private String buildRejectedObjectKey(String sourceObjectKey) {
        Objects.requireNonNull(sourceObjectKey, "Source object key is required");

        if (!sourceObjectKey.endsWith("/" + SOURCE_FILE_NAME)) {
            throw new IllegalArgumentException(
                    "Source object key must end with /" + SOURCE_FILE_NAME
            );
        }

        return sourceObjectKey.substring(
                0,
                sourceObjectKey.length() - SOURCE_FILE_NAME.length()
        ) + REJECTED_FILE_NAME;
    }
}