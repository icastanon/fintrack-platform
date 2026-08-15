package com.fintrack.workerservice.transactionimport.batch.stream;

import com.fintrack.workerservice.transactionimport.service.TransactionImportProcessingLeaseManager;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStream;

public class TransactionImportChunkCommitFence implements ItemStream {

    private final TransactionImportProcessingLeaseManager processingLeaseManager;
    private final Long importId;
    private final Long accountId;
    private final Long userId;
    private final String processingOwner;
    private final long processingFencingToken;

    public TransactionImportChunkCommitFence(TransactionImportProcessingLeaseManager processingLeaseManager,
                                             Long importId,
                                             Long accountId,
                                             Long userId,
                                             String processingOwner,
                                             long processingFencingToken) {
        this.processingLeaseManager = processingLeaseManager;
        this.importId = importId;
        this.accountId = accountId;
        this.userId = userId;
        this.processingOwner = processingOwner;
        this.processingFencingToken = processingFencingToken;
    }

    @Override
    public void update(ExecutionContext ignoredExecutionContext) {
        processingLeaseManager.assertActive(
                importId,
                accountId,
                userId,
                processingOwner,
                processingFencingToken
        );
    }
}