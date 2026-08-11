package com.fintrack.workerservice.transactionimport.batch.reader;

import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportCsvRow;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.file.transform.IncorrectTokenCountException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionImportCsvLineMapperTest {

    private final TransactionImportCsvLineMapper lineMapper = new TransactionImportCsvLineMapper();

    @Test
    void mapLineMapsEveryCsvColumnAndPhysicalRowNumber() {
        TransactionImportCsvRow result = lineMapper.mapLine(
                "2026-08-01,EXPENSE,42.75,Publix,Weekly groceries",
                7
        );

        assertThat(result.getRowNumber()).isEqualTo(7);
        assertThat(result.getTransactionDate()).isEqualTo("2026-08-01");
        assertThat(result.getTransactionType()).isEqualTo("EXPENSE");
        assertThat(result.getAmount()).isEqualTo("42.75");
        assertThat(result.getMerchant()).isEqualTo("Publix");
        assertThat(result.getDescription()).isEqualTo("Weekly groceries");
    }

    @Test
    void mapLineSupportsQuotedFieldsContainingCommas() {
        TransactionImportCsvRow result = lineMapper.mapLine(
                "2026-08-01,EXPENSE,42.75,Publix,\"Groceries, cleaning products, and milk\"",
                2
        );

        assertThat(result.getMerchant()).isEqualTo("Publix");
        assertThat(result.getDescription()).isEqualTo("Groceries, cleaning products, and milk");
    }

    @Test
    void mapLinePreservesEmptyOptionalFields() {
        TransactionImportCsvRow result = lineMapper.mapLine(
                "2026-08-01,EXPENSE,42.75,,",
                2
        );

        assertThat(result.getMerchant()).isEmpty();
        assertThat(result.getDescription()).isEmpty();
    }

    @Test
    void mapLineThrowsWhenColumnCountIsIncorrect() {
        assertThatThrownBy(() ->
                lineMapper.mapLine("2026-08-01,EXPENSE,42.75,Publix", 2)
        )
                .isInstanceOf(IncorrectTokenCountException.class);
    }
}