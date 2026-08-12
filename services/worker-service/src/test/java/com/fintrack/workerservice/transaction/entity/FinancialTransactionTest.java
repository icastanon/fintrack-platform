package com.fintrack.workerservice.transaction.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FinancialTransactionTest {

    private static final Long IMPORT_ID = 41L;
    private static final int IMPORT_ROW_NUMBER = 7;
    private static final Long ACCOUNT_ID = 22L;
    private static final Long CATEGORY_ID = 2L;
    private static final BigDecimal AMOUNT = new BigDecimal("42.75");
    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2026, 8, 1);

    @Test
    void createImportedInitializesProcessedCategorizedImportTransaction() {
        FinancialTransaction transaction = createImportedTransaction();

        assertThat(transaction.getImportId()).isEqualTo(IMPORT_ID);
        assertThat(transaction.getImportRowNumber()).isEqualTo(IMPORT_ROW_NUMBER);
        assertThat(transaction.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(transaction.getCategoryId()).isEqualTo(CATEGORY_ID);
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(transaction.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(transaction.getMerchant()).isEqualTo("Publix");
        assertThat(transaction.getDescription()).isEqualTo("Weekly groceries");
        assertThat(transaction.getTransactionDate()).isEqualTo(TRANSACTION_DATE);
        assertThat(transaction.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(transaction.getSource()).isEqualTo(TransactionSource.IMPORT);
        assertThat(transaction.isManualCategoryOverride()).isFalse();
    }

    @Test
    void createImportedRejectsMissingImportId() {
        assertThatNullPointerException()
                .isThrownBy(() -> FinancialTransaction.createImported(null, IMPORT_ROW_NUMBER,
                        ACCOUNT_ID, CATEGORY_ID, TransactionType.EXPENSE, AMOUNT,
                        "Publix", "Weekly groceries", TRANSACTION_DATE))
                .withMessage("Import ID is required");
    }

    @Test
    void createImportedRejectsInvalidImportRowNumber() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FinancialTransaction.createImported(IMPORT_ID, 1,
                        ACCOUNT_ID, CATEGORY_ID, TransactionType.EXPENSE, AMOUNT,
                        "Publix", "Weekly groceries", TRANSACTION_DATE))
                .withMessage("Import row number must be at least 2");
    }

    @Test
    void createImportedRejectsMissingAccountId() {
        assertThatNullPointerException()
                .isThrownBy(() -> FinancialTransaction.createImported(IMPORT_ID, IMPORT_ROW_NUMBER,
                        null, CATEGORY_ID, TransactionType.EXPENSE, AMOUNT,
                        "Publix", "Weekly groceries", TRANSACTION_DATE))
                .withMessage("Account ID is required");
    }

    @Test
    void createImportedRejectsMissingCategoryId() {
        assertThatNullPointerException()
                .isThrownBy(() -> FinancialTransaction.createImported(IMPORT_ID, IMPORT_ROW_NUMBER,
                        ACCOUNT_ID, null, TransactionType.EXPENSE, AMOUNT,
                        "Publix", "Weekly groceries", TRANSACTION_DATE))
                .withMessage("Category ID is required");
    }

    @Test
    void createImportedRejectsMissingTransactionType() {
        assertThatNullPointerException()
                .isThrownBy(() -> FinancialTransaction.createImported(IMPORT_ID, IMPORT_ROW_NUMBER,
                        ACCOUNT_ID, CATEGORY_ID, null, AMOUNT,
                        "Publix", "Weekly groceries", TRANSACTION_DATE))
                .withMessage("Transaction type is required");
    }

    @Test
    void createImportedRejectsMissingAmount() {
        assertThatNullPointerException()
                .isThrownBy(() -> FinancialTransaction.createImported(IMPORT_ID, IMPORT_ROW_NUMBER,
                        ACCOUNT_ID, CATEGORY_ID, TransactionType.EXPENSE, null,
                        "Publix", "Weekly groceries", TRANSACTION_DATE))
                .withMessage("Amount is required");
    }

    @Test
    void createImportedRejectsMissingTransactionDate() {
        assertThatNullPointerException()
                .isThrownBy(() -> FinancialTransaction.createImported(IMPORT_ID, IMPORT_ROW_NUMBER,
                        ACCOUNT_ID, CATEGORY_ID, TransactionType.EXPENSE, AMOUNT,
                        "Publix", "Weekly groceries", null))
                .withMessage("Transaction date is required");
    }

    private FinancialTransaction createImportedTransaction() {
        return FinancialTransaction.createImported(IMPORT_ID, IMPORT_ROW_NUMBER,
                ACCOUNT_ID, CATEGORY_ID, TransactionType.EXPENSE, AMOUNT,
                "Publix", "Weekly groceries", TRANSACTION_DATE);
    }
}