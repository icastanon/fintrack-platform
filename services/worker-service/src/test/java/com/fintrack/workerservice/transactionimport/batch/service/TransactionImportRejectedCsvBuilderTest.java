package com.fintrack.workerservice.transactionimport.batch.service;

import com.fintrack.workerservice.transactionimport.entity.TransactionImportRejectedRowStaging;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionImportRejectedCsvBuilderTest {

    private static final Long IMPORT_ID = 17L;

    private final TransactionImportRejectedCsvBuilder csvBuilder =
            new TransactionImportRejectedCsvBuilder();

    @Test
    void buildCreatesUtf8CsvWithHeaderAndOrderedRows() {
        TransactionImportRejectedRowStaging firstRow =
                TransactionImportRejectedRowStaging.create(
                        IMPORT_ID,
                        2,
                        "2026-08-10,EXPENSE,invalid,STARBUCKS,Coffee",
                        "Row 2: amount must be a valid decimal number"
                );

        TransactionImportRejectedRowStaging secondRow =
                TransactionImportRejectedRowStaging.create(
                        IMPORT_ID,
                        5,
                        "2026-08-11,EXPENSE,12.50,Publix,Groceries",
                        "Row 5: transaction date is invalid"
                );

        byte[] result = csvBuilder.build(List.of(firstRow, secondRow));

        String expected =
                "row_number,raw_record,failure_reason\n"
                        + "2,\"2026-08-10,EXPENSE,invalid,STARBUCKS,Coffee\","
                        + "\"Row 2: amount must be a valid decimal number\"\n"
                        + "5,\"2026-08-11,EXPENSE,12.50,Publix,Groceries\","
                        + "\"Row 5: transaction date is invalid\"\n";

        assertThat(new String(result, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    void buildEscapesQuotationMarksAndCommasInsideFields() {
        TransactionImportRejectedRowStaging rejectedRow =
                TransactionImportRejectedRowStaging.create(
                        IMPORT_ID,
                        4,
                        "2026-08-10,EXPENSE,12.50,STARBUCKS,\"Coffee, large\"",
                        "Row 4: invalid \"description\", please correct it"
                );

        byte[] result = csvBuilder.build(List.of(rejectedRow));

        String expected =
                "row_number,raw_record,failure_reason\n"
                        + "4,\"2026-08-10,EXPENSE,12.50,STARBUCKS,"
                        + "\"\"Coffee, large\"\"\","
                        + "\"Row 4: invalid \"\"description\"\", please correct it\"\n";

        assertThat(new String(result, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    void buildReturnsHeaderWhenThereAreNoRejectedRows() {
        byte[] result = csvBuilder.build(List.of());

        assertThat(new String(result, StandardCharsets.UTF_8))
                .isEqualTo("row_number,raw_record,failure_reason\n");
    }

    @Test
    void buildRejectsNullList() {
        assertThatThrownBy(() -> csvBuilder.build(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Rejected rows are required");
    }

    @Test
    void buildRejectsNullRow() {
        List<TransactionImportRejectedRowStaging> rejectedRows =
                Arrays.asList((TransactionImportRejectedRowStaging) null);

        assertThatThrownBy(() -> csvBuilder.build(rejectedRows))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Rejected row is required");
    }
}