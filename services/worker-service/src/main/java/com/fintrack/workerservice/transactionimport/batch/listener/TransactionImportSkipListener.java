package com.fintrack.workerservice.transactionimport.batch.listener;

import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportCsvRow;
import com.fintrack.workerservice.transactionimport.batch.model.ValidatedTransactionImportRow;
import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportRejectedRowStagingService;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportRowValidationException;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.transform.IncorrectTokenCountException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@StepScope
public class TransactionImportSkipListener implements SkipListener<TransactionImportCsvRow, ValidatedTransactionImportRow> {

    private static final int EXPECTED_COLUMN_COUNT = 5;

    private final TransactionImportRejectedRowStagingService rejectedRowStagingService;
    private final Long importId;

    public TransactionImportSkipListener(TransactionImportRejectedRowStagingService rejectedRowStagingService,
                                         @Value("#{jobParameters['importId']}") Long importId) {
        this.rejectedRowStagingService = Objects.requireNonNull(rejectedRowStagingService, "Rejected-row staging service is required");
        this.importId = Objects.requireNonNull(importId, "Import ID is required");
    }

    @Override
    public void onSkipInRead(Throwable throwable) {
        if (!(throwable instanceof FlatFileParseException parseException)) {
            throw new IllegalStateException(
                    "Unexpected transaction-import read skip type: "
                            + throwable.getClass().getName(),
                    throwable
            );
        }

        rejectedRowStagingService.stage(
                importId,
                parseException.getLineNumber(),
                parseException.getInput(),
                buildReadFailureReason(parseException)
        );
    }

    @Override
    public void onSkipInProcess(TransactionImportCsvRow row, Throwable throwable) {
        if (!(throwable instanceof TransactionImportRowValidationException validationException)) {
            throw new IllegalStateException(
                    "Unexpected transaction-import process skip type: "
                            + throwable.getClass().getName(),
                    throwable
            );
        }

        rejectedRowStagingService.stage(
                importId,
                row.getRowNumber(),
                row.getRawRecord(),
                validationException.getMessage()
        );
    }

    private String buildReadFailureReason(FlatFileParseException parseException) {
        if (parseException.getCause() instanceof IncorrectTokenCountException tokenCountException) {
            return "Row " + parseException.getLineNumber()
                    + ": CSV record must contain exactly " + EXPECTED_COLUMN_COUNT
                    + " columns but contained " + tokenCountException.getActualCount();
        }

        return "Row " + parseException.getLineNumber()
                + ": CSV record could not be parsed";
    }
}