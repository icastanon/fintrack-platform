package com.fintrack.apiservice.transactionimport.validation;

import com.fintrack.apiservice.transactionimport.exception.InvalidTransactionImportFileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionImportFileValidatorTest {

    private TransactionImportFileValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TransactionImportFileValidator(DataSize.ofBytes(100));
    }

    @Test
    void validateReturnsFilenameForValidCsv() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                "date,type,amount".getBytes(StandardCharsets.UTF_8)
        );

        String result = validator.validate(file);

        assertThat(result).isEqualTo("transactions.csv");
    }

    @Test
    void validateNormalizesClientPathAndContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "C:\\fakepath\\transactions.CSV",
                "TEXT/CSV; charset=UTF-8",
                "date,type,amount".getBytes(StandardCharsets.UTF_8)
        );

        String result = validator.validate(file);

        assertThat(result).isEqualTo("transactions.CSV");
    }

    @Test
    void validateRejectsMissingFile() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidTransactionImportFileException.class)
                .hasMessage("A non-empty CSV file is required");
    }

    @Test
    void validateRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                new byte[0]
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidTransactionImportFileException.class)
                .hasMessage("A non-empty CSV file is required");
    }

    @Test
    void validateRejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                new byte[101]
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidTransactionImportFileException.class)
                .hasMessage("The CSV file exceeds the maximum allowed size");
    }

    @Test
    void validateRejectsMissingFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                null,
                "text/csv",
                "date,type,amount".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidTransactionImportFileException.class)
                .hasMessage("The uploaded file must have a filename");
    }

    @Test
    void validateRejectsFilenameLongerThanDatabaseLimit() {
        String longFilename = "a".repeat(252) + ".csv";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                longFilename,
                "text/csv",
                "date,type,amount".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidTransactionImportFileException.class)
                .hasMessage("The uploaded filename cannot exceed 255 characters");
    }

    @Test
    void validateRejectsNonCsvExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.txt",
                "text/csv",
                "date,type,amount".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidTransactionImportFileException.class)
                .hasMessage("The uploaded file must use the .csv extension");
    }

    @Test
    void validateRejectsMissingContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                null,
                "date,type,amount".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidTransactionImportFileException.class)
                .hasMessage("The uploaded file must have a content type");
    }

    @Test
    void validateRejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "application/pdf",
                "date,type,amount".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidTransactionImportFileException.class)
                .hasMessage("The uploaded file has an unsupported content type");
    }
}