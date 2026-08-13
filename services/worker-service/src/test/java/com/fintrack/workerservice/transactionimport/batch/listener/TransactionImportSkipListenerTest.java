package com.fintrack.workerservice.transactionimport.batch.listener;

import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportCsvRow;
import com.fintrack.workerservice.transactionimport.batch.service.TransactionImportRejectedRowStagingService;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportRowValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.transform.IncorrectTokenCountException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TransactionImportSkipListenerTest {

    private static final Long IMPORT_ID = 17L;
    private static final int ROW_NUMBER = 4;
    private static final String RAW_RECORD =
            "2026-08-10,EXPENSE,invalid,STARBUCKS,Coffee";

    @Mock
    private TransactionImportRejectedRowStagingService rejectedRowStagingService;

    private TransactionImportSkipListener skipListener;

    @BeforeEach
    void setUp() {
        skipListener = new TransactionImportSkipListener(
                rejectedRowStagingService,
                IMPORT_ID
        );
    }

    @Test
    void onSkipInProcessStagesOriginalRecordAndValidationReason() {
        TransactionImportCsvRow row = csvRow();
        TransactionImportRowValidationException exception =
                new TransactionImportRowValidationException(
                        ROW_NUMBER,
                        "amount must be a valid decimal number"
                );

        skipListener.onSkipInProcess(row, exception);

        verify(rejectedRowStagingService).stage(
                IMPORT_ID,
                ROW_NUMBER,
                RAW_RECORD,
                "Row 4: amount must be a valid decimal number"
        );
    }

    @Test
    void onSkipInReadStagesIncorrectColumnCount() {
        String rawRecord = "2026-08-10,EXPENSE,12.50,STARBUCKS";
        IncorrectTokenCountException cause =
                new IncorrectTokenCountException(5, 4, rawRecord);

        FlatFileParseException exception = new FlatFileParseException(
                "Failed to parse CSV record",
                cause,
                rawRecord,
                ROW_NUMBER
        );

        skipListener.onSkipInRead(exception);

        verify(rejectedRowStagingService).stage(
                IMPORT_ID,
                ROW_NUMBER,
                rawRecord,
                "Row 4: CSV record must contain exactly 5 columns but contained 4"
        );
    }

    @Test
    void onSkipInReadStagesGenericParseFailure() {
        String rawRecord = "malformed,csv,record";

        FlatFileParseException exception = new FlatFileParseException(
                "Failed to parse CSV record",
                new IllegalArgumentException("Malformed quoting"),
                rawRecord,
                ROW_NUMBER
        );

        skipListener.onSkipInRead(exception);

        verify(rejectedRowStagingService).stage(
                IMPORT_ID,
                ROW_NUMBER,
                rawRecord,
                "Row 4: CSV record could not be parsed"
        );
    }

    @Test
    void onSkipInProcessRejectsUnexpectedExceptionType() {
        TransactionImportCsvRow row = csvRow();
        IllegalStateException cause = new IllegalStateException("Unexpected failure");

        assertThatThrownBy(() -> skipListener.onSkipInProcess(row, cause))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected transaction-import process skip type")
                .hasCause(cause);

        verifyNoInteractions(rejectedRowStagingService);
    }

    @Test
    void onSkipInReadRejectsUnexpectedExceptionType() {
        IllegalStateException cause = new IllegalStateException("Unexpected failure");

        assertThatThrownBy(() -> skipListener.onSkipInRead(cause))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected transaction-import read skip type")
                .hasCause(cause);

        verifyNoInteractions(rejectedRowStagingService);
    }

    private TransactionImportCsvRow csvRow() {
        return new TransactionImportCsvRow(
                ROW_NUMBER,
                "2026-08-10",
                "EXPENSE",
                "invalid",
                "STARBUCKS",
                "Coffee",
                RAW_RECORD
        );
    }
}