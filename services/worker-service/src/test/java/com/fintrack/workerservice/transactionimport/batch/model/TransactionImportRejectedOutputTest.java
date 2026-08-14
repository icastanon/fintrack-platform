package com.fintrack.workerservice.transactionimport.batch.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionImportRejectedOutputTest {

    @Test
    void noneReturnsOutputWithoutRejectedRowsOrObjectKey() {
        TransactionImportRejectedOutput output = TransactionImportRejectedOutput.none();

        assertThat(output.exists()).isFalse();
        assertThat(output.getRejectedRowCount()).isZero();
        assertThat(output.getObjectKey()).isNull();
    }

    @Test
    void uploadedReturnsRejectedRowCountAndObjectKey() {
        TransactionImportRejectedOutput output =
                TransactionImportRejectedOutput.uploaded(3, "imports/9/test/rejected.csv");

        assertThat(output.exists()).isTrue();
        assertThat(output.getRejectedRowCount()).isEqualTo(3);
        assertThat(output.getObjectKey()).isEqualTo("imports/9/test/rejected.csv");
    }

    @Test
    void uploadedRejectsNonPositiveRejectedRowCount() {
        assertThatThrownBy(() -> TransactionImportRejectedOutput.uploaded(0, "imports/9/test/rejected.csv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rejected row count must be positive for an uploaded output");
    }

    @Test
    void uploadedRejectsNullObjectKey() {
        assertThatThrownBy(() -> TransactionImportRejectedOutput.uploaded(1, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Rejected output object key is required");
    }

    @Test
    void uploadedRejectsBlankObjectKey() {
        assertThatThrownBy(() -> TransactionImportRejectedOutput.uploaded(1, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rejected output object key cannot be blank");
    }
}