package com.fintrack.workerservice.transactionimport.batch.stream;

import com.fintrack.workerservice.transactionimport.exception.TransactionImportProcessingLeaseLostException;
import com.fintrack.workerservice.transactionimport.service.TransactionImportProcessingLeaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionImportChunkCommitFenceTest {

    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 22L;
    private static final Long USER_ID = 9L;
    private static final String PROCESSING_OWNER = "worker-attempt-123";
    private static final long PROCESSING_FENCING_TOKEN = 3L;

    @Mock
    private TransactionImportProcessingLeaseManager processingLeaseManager;

    private TransactionImportChunkCommitFence commitFence;

    @BeforeEach
    void setUp() {
        commitFence = new TransactionImportChunkCommitFence(processingLeaseManager,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                PROCESSING_FENCING_TOKEN);
    }

    @Test
    void updateValidatesCurrentProcessingOwnership() {
        commitFence.update(new ExecutionContext());

        verify(processingLeaseManager).assertActive(IMPORT_ID,
                ACCOUNT_ID,
                USER_ID,
                PROCESSING_OWNER,
                PROCESSING_FENCING_TOKEN);
    }

    @Test
    void updatePropagatesProcessingLeaseLoss() {
        TransactionImportProcessingLeaseLostException leaseLostException =
                new TransactionImportProcessingLeaseLostException(IMPORT_ID,
                        PROCESSING_OWNER,
                        PROCESSING_FENCING_TOKEN);

        doThrow(leaseLostException)
                .when(processingLeaseManager)
                .assertActive(IMPORT_ID,
                        ACCOUNT_ID,
                        USER_ID,
                        PROCESSING_OWNER,
                        PROCESSING_FENCING_TOKEN);

        assertThatThrownBy(() -> commitFence.update(new ExecutionContext()))
                .isSameAs(leaseLostException);
    }
}