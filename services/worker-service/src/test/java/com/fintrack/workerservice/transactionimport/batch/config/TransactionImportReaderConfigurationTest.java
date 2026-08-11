package com.fintrack.workerservice.transactionimport.batch.config;

import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportCsvRow;
import com.fintrack.workerservice.transactionimport.batch.reader.TransactionImportCsvHeaderValidator;
import com.fintrack.workerservice.transactionimport.batch.reader.TransactionImportCsvLineMapper;
import com.fintrack.workerservice.transactionimport.exception.InvalidTransactionImportHeaderException;
import com.fintrack.workerservice.transactionimport.storage.TransactionImportStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportReaderConfigurationTest {

    private static final String SOURCE_OBJECT_KEY = "imports/9/abc/source.csv";

    @Mock
    private TransactionImportStorageService transactionImportStorageService;

    private TransactionImportReaderConfiguration readerConfiguration;

    @BeforeEach
    void setUp() {
        readerConfiguration = new TransactionImportReaderConfiguration(transactionImportStorageService,
                new TransactionImportCsvHeaderValidator(),
                new TransactionImportCsvLineMapper());
    }

    @Test
    void transactionImportCsvReaderReadsRowsFromSourceAndSkipsHeader() throws Exception {
        String csv = """
                transaction_date,transaction_type,amount,merchant,description
                2026-08-01,EXPENSE,42.75,Publix,Weekly groceries
                2026-08-02,INCOME,2500.00,Employer,Salary
                """;

        when(transactionImportStorageService.openSource(SOURCE_OBJECT_KEY))
                .thenReturn(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        FlatFileItemReader<TransactionImportCsvRow> reader =
                readerConfiguration.transactionImportCsvReader(SOURCE_OBJECT_KEY);

        reader.open(new ExecutionContext());

        try {
            TransactionImportCsvRow first = reader.read();
            TransactionImportCsvRow second = reader.read();
            TransactionImportCsvRow end = reader.read();

            assertThat(first.getRowNumber()).isEqualTo(2);
            assertThat(first.getMerchant()).isEqualTo("Publix");
            assertThat(first.getAmount()).isEqualTo("42.75");

            assertThat(second.getRowNumber()).isEqualTo(3);
            assertThat(second.getTransactionType()).isEqualTo("INCOME");
            assertThat(second.getDescription()).isEqualTo("Salary");

            assertThat(end).isNull();
        } finally {
            reader.close();
        }

        verify(transactionImportStorageService).openSource(SOURCE_OBJECT_KEY);
    }

    @Test
    void transactionImportCsvReaderRejectsInvalidHeaderWhenOpened() throws Exception {
        String csv = """
                date,type,amount,merchant,description
                2026-08-01,EXPENSE,42.75,Publix,Weekly groceries
                """;

        when(transactionImportStorageService.openSource(SOURCE_OBJECT_KEY))
                .thenReturn(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        FlatFileItemReader<TransactionImportCsvRow> reader =
                readerConfiguration.transactionImportCsvReader(SOURCE_OBJECT_KEY);

        try {
            assertThatThrownBy(() -> reader.open(new ExecutionContext()))
                    .isInstanceOf(ItemStreamException.class)
                    .hasCauseInstanceOf(InvalidTransactionImportHeaderException.class);
        } finally {
            reader.close();
        }
    }
}