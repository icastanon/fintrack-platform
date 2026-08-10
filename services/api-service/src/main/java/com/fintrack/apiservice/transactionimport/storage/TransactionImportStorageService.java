package com.fintrack.apiservice.transactionimport.storage;

import com.fintrack.apiservice.transactionimport.exception.TransactionImportStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;

@Service
public class TransactionImportStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionImportStorageService.class);

    private final S3Client s3Client;
    private final String importBucket;

    public TransactionImportStorageService(S3Client s3Client,
                                           @Value("${fintrack.s3.import-bucket}")
                                           String importBucket) {
        this.s3Client = s3Client;
        this.importBucket = importBucket;
    }

    public String upload(Long userId, InputStream inputStream, long contentLength, String contentType) {
        String objectKey = "imports/" + userId + "/" + UUID.randomUUID() + "/source.csv";

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(importBucket)
                .key(objectKey)
                .contentLength(contentLength)
                .contentType(contentType)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
            return objectKey;
        } catch (SdkException | UncheckedIOException exception) {
            throw new TransactionImportStorageException("Failed to upload the transaction import file", exception);
        }
    }

    public void deleteQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(importBucket)
                .key(objectKey)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            LOGGER.warn("Failed to clean up transaction import object: objectKey={}", objectKey, exception);
        }
    }
}