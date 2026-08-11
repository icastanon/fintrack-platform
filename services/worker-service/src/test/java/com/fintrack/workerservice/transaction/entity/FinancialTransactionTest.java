package com.fintrack.workerservice.transaction.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FinancialTransactionTest {

    private static final Long ACCOUNT_ID = 22L;
    private static final BigDecimal AMOUNT = new BigDecimal("42.75");
    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2026, 8, 1);

    @Test
    void createImportedInitializesPendingImportTransaction() {
        FinancialTransaction transaction = createImportedTransaction();

        assertThat(transaction.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(transaction.getCategoryId()).isNull();
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(transaction.getAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(transaction.getMerchant()).isEqualTo("Publix");
        assertThat(transaction.getDescription()).isEqualTo("Weekly groceries");
        assertThat(transaction.getTransactionDate()).isEqualTo(TRANSACTION_DATE);
        assertThat(transaction.getProcessingStatus()).isEqualTo(ProcessingStatus.PENDING);
        assertThat(transaction.getSource()).isEqualTo(TransactionSource.IMPORT);
        assertThat(transaction.isManualCategoryOverride()).isFalse();
    }

    @Test
    void createImportedRejectsMissingAccountId() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        FinancialTransaction.createImported(null,
                                TransactionType.EXPENSE,
                                AMOUNT,
                                "Publix",
                                "Weekly groceries",
                                TRANSACTION_DATE)
                )
                .withMessage("Account ID is required");
    }

    @Test
    void createImportedRejectsMissingTransactionType() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        FinancialTransaction.createImported(ACCOUNT_ID,
                                null,
                                AMOUNT,
                                "Publix",
                                "Weekly groceries",
                                TRANSACTION_DATE)
                )
                .withMessage("Transaction type is required");
    }

    @Test
    void createImportedRejectsMissingAmount() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        FinancialTransaction.createImported(ACCOUNT_ID,
                                TransactionType.EXPENSE,
                                null,
                                "Publix",
                                "Weekly groceries",
                                TRANSACTION_DATE)
                )
                .withMessage("Amount is required");
    }

    @Test
    void createImportedRejectsMissingTransactionDate() {
        assertThatNullPointerException()
                .isThrownBy(() ->
                        FinancialTransaction.createImported(ACCOUNT_ID,
                                TransactionType.EXPENSE,
                                AMOUNT,
                                "Publix",
                                "Weekly groceries",
                                null)
                )
                .withMessage("Transaction date is required");
    }

    private FinancialTransaction createImportedTransaction() {
        return FinancialTransaction.createImported(ACCOUNT_ID,
                TransactionType.EXPENSE,
                AMOUNT,
                "Publix",
                "Weekly groceries",
                TRANSACTION_DATE);
    }
}