package com.fintrack.workerservice.transactionimport.storage;

import com.fintrack.workerservice.transactionimport.exception.TransactionImportStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;

@Service
public class TransactionImportStorageService {

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
}