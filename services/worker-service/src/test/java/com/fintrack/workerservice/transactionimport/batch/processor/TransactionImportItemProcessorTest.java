package com.fintrack.workerservice.transactionimport.batch.processor;

import com.fintrack.workerservice.category.cache.model.CategorizationRuleCacheSnapshot;
import com.fintrack.workerservice.category.service.CategorizationService;
import com.fintrack.workerservice.transaction.entity.TransactionType;
import com.fintrack.workerservice.transactionimport.batch.model.TransactionImportCsvRow;
import com.fintrack.workerservice.transactionimport.batch.model.ValidatedTransactionImportRow;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportRowValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportItemProcessorTest {

    private static final int ROW_NUMBER = 7;
    private static final String VALID_DATE = LocalDate.now().minusDays(1).toString();
    private static final CategorizationRuleCacheSnapshot RULE_SNAPSHOT =
            new CategorizationRuleCacheSnapshot(9L, List.of());

    @Mock
    private CategorizationService categorizationService;

    private TransactionImportItemProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TransactionImportItemProcessor(categorizationService, RULE_SNAPSHOT);
    }

    @Test
    void processConvertsNormalizesAndCategorizesValidRow() {
        TransactionImportCsvRow row = row(VALID_DATE,
                " expense ",
                " 42.75 ",
                "  Publix  ",
                "  Weekly groceries  ");

        when(categorizationService.categorizeMerchant("Publix", RULE_SNAPSHOT)).thenReturn(2L);

        ValidatedTransactionImportRow result = processor.process(row);

        assertThat(result.getRowNumber()).isEqualTo(ROW_NUMBER);
        assertThat(result.getTransactionDate()).isEqualTo(LocalDate.parse(VALID_DATE));
        assertThat(result.getTransactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(result.getAmount()).isEqualByComparingTo("42.75");
        assertThat(result.getMerchant()).isEqualTo("Publix");
        assertThat(result.getDescription()).isEqualTo("Weekly groceries");
        assertThat(result.getCategoryId()).isEqualTo(2L);

        verify(categorizationService).categorizeMerchant("Publix", RULE_SNAPSHOT);
    }

    @Test
    void processConvertsBlankOptionalFieldsToNullAndUsesFallbackCategory() {
        TransactionImportCsvRow row = row(VALID_DATE,
                "INCOME",
                "2500.00",
                "   ",
                "");

        when(categorizationService.categorizeMerchant(null, RULE_SNAPSHOT)).thenReturn(9L);

        ValidatedTransactionImportRow result = processor.process(row);

        assertThat(result.getMerchant()).isNull();
        assertThat(result.getDescription()).isNull();
        assertThat(result.getCategoryId()).isEqualTo(9L);

        verify(categorizationService).categorizeMerchant(null, RULE_SNAPSHOT);
    }

    @Test
    void processRejectsInvalidDateFormat() {
        TransactionImportCsvRow row = row("08/01/2026",
                "EXPENSE",
                "42.75",
                "Publix",
                "Groceries");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(TransactionImportRowValidationException.class)
                .hasMessage("Row 7: transaction date must use YYYY-MM-DD");
    }

    @Test
    void processRejectsFutureDate() {
        TransactionImportCsvRow row = row(LocalDate.now().plusDays(1).toString(),
                "EXPENSE",
                "42.75",
                "Publix",
                "Groceries");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(TransactionImportRowValidationException.class)
                .hasMessage("Row 7: transaction date cannot be in the future");
    }

    @Test
    void processRejectsUnsupportedTransactionType() {
        TransactionImportCsvRow row = row(VALID_DATE,
                "TRANSFER",
                "42.75",
                "Publix",
                "Groceries");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(TransactionImportRowValidationException.class)
                .hasMessage("Row 7: transaction type must be INCOME or EXPENSE");
    }

    @Test
    void processRejectsInvalidDecimalAmount() {
        TransactionImportCsvRow row = row(VALID_DATE,
                "EXPENSE",
                "forty-two",
                "Publix",
                "Groceries");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(TransactionImportRowValidationException.class)
                .hasMessage("Row 7: amount must be a valid decimal number");
    }

    @Test
    void processRejectsAmountBelowMinimum() {
        TransactionImportCsvRow row = row(VALID_DATE,
                "EXPENSE",
                "0.00",
                "Publix",
                "Groceries");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(TransactionImportRowValidationException.class)
                .hasMessage("Row 7: amount must be at least 0.01");
    }

    @Test
    void processRejectsAmountWithTooManyDecimalPlaces() {
        TransactionImportCsvRow row = row(VALID_DATE,
                "EXPENSE",
                "42.755",
                "Publix",
                "Groceries");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(TransactionImportRowValidationException.class)
                .hasMessage("Row 7: amount must have at most 17 integer digits and 2 decimal places");
    }

    @Test
    void processRejectsAmountWithTooManyIntegerDigits() {
        TransactionImportCsvRow row = row(VALID_DATE,
                "EXPENSE",
                "123456789012345678.00",
                "Publix",
                "Groceries");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(TransactionImportRowValidationException.class)
                .hasMessage("Row 7: amount must have at most 17 integer digits and 2 decimal places");
    }

    @Test
    void processRejectsMerchantAboveMaximumLength() {
        TransactionImportCsvRow row = row(VALID_DATE,
                "EXPENSE",
                "42.75",
                "m".repeat(201),
                "Groceries");

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(TransactionImportRowValidationException.class)
                .hasMessage("Row 7: merchant must not exceed 200 characters");
    }

    @Test
    void processRejectsDescriptionAboveMaximumLength() {
        TransactionImportCsvRow row = row(VALID_DATE,
                "EXPENSE",
                "42.75",
                "Publix",
                "d".repeat(501));

        assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(TransactionImportRowValidationException.class)
                .hasMessage("Row 7: description must not exceed 500 characters");
    }

    private TransactionImportCsvRow row(String transactionDate, String transactionType,
                                        String amount, String merchant, String description) {
        return new TransactionImportCsvRow(
                ROW_NUMBER,
                transactionDate,
                transactionType,
                amount,
                merchant,
                description,
                "raw-record"
        );
    }
}