package com.fintrack.workerservice.transactionimport.batch.reader;

import com.fintrack.workerservice.transactionimport.exception.InvalidTransactionImportHeaderException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionImportCsvHeaderValidatorTest {

    private static final String EXPECTED_HEADER =
            "transaction_date,transaction_type,amount,merchant,description";

    private final TransactionImportCsvHeaderValidator headerValidator =
            new TransactionImportCsvHeaderValidator();

    @Test
    void handleLineAcceptsExpectedHeader() {
        assertThatCode(() -> headerValidator.handleLine(EXPECTED_HEADER))
                .doesNotThrowAnyException();
    }

    @Test
    void handleLineAcceptsExpectedHeaderWithUtf8ByteOrderMark() {
        assertThatCode(() -> headerValidator.handleLine("\uFEFF" + EXPECTED_HEADER))
                .doesNotThrowAnyException();
    }

    @Test
    void handleLineRejectsHeaderWithIncorrectColumnOrder() {
        String invalidHeader =
                "transaction_type,transaction_date,amount,merchant,description";

        assertThatThrownBy(() -> headerValidator.handleLine(invalidHeader))
                .isInstanceOf(InvalidTransactionImportHeaderException.class)
                .hasMessage("Transaction import CSV header must be: " + EXPECTED_HEADER);
    }

    @Test
    void handleLineRejectsHeaderWithMissingColumn() {
        String invalidHeader =
                "transaction_date,transaction_type,amount,merchant";

        assertThatThrownBy(() -> headerValidator.handleLine(invalidHeader))
                .isInstanceOf(InvalidTransactionImportHeaderException.class)
                .hasMessage("Transaction import CSV header must be: " + EXPECTED_HEADER);
    }
}