package com.fintrack.workerservice.transactionimport.batch.writer;

import com.fintrack.workerservice.account.entity.AccountStatus;
import com.fintrack.workerservice.account.entity.FinancialAccount;
import com.fintrack.workerservice.account.repository.FinancialAccountRepository;
import com.fintrack.workerservice.budget.model.BudgetEvaluationResult;
import com.fintrack.workerservice.budget.model.BudgetStatus;
import com.fintrack.workerservice.budget.service.BudgetEvaluationService;
import com.fintrack.workerservice.notification.service.NotificationService;
import com.fintrack.workerservice.transaction.entity.FinancialTransaction;
import com.fintrack.workerservice.transaction.entity.ProcessingStatus;
import com.fintrack.workerservice.transaction.entity.TransactionSource;
import com.fintrack.workerservice.transaction.entity.TransactionType;
import com.fintrack.workerservice.transaction.repository.FinancialTransactionRepository;
import com.fintrack.workerservice.transactionimport.batch.model.ValidatedTransactionImportRow;
import com.fintrack.workerservice.transactionimport.exception.TransactionImportAccountUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionImportItemWriterTest {

    private static final Long IMPORT_ID = 41L;
    private static final Long ACCOUNT_ID = 22L;
    private static final Long USER_ID = 9L;
    private static final LocalDate AUGUST = LocalDate.of(2026, 8, 1);
    private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 1);

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private BudgetEvaluationService budgetEvaluationService;

    @Mock
    private NotificationService notificationService;

    private TransactionImportItemWriter writer;

    @BeforeEach
    void setUp() {
        writer = new TransactionImportItemWriter(
                financialAccountRepository,
                financialTransactionRepository,
                budgetEvaluationService,
                notificationService,
                IMPORT_ID,
                ACCOUNT_ID,
                USER_ID);
    }

    @Test
    void writeLocksAccountAppliesBalancesPersistsTransactionsAndEvaluatesBudget() {
        FinancialAccount account = activeAccount();

        BudgetEvaluationResult evaluation = new BudgetEvaluationResult(
                51L,
                new BigDecimal("100.00"),
                new BigDecimal("82.75"),
                new BigDecimal("82.75"),
                BudgetStatus.WARNING);

        when(budgetEvaluationService.evaluate(USER_ID, 2L, AUGUST))
                .thenReturn(Optional.of(evaluation));

        ValidatedTransactionImportRow expense = row(
                2,
                AUGUST,
                TransactionType.EXPENSE,
                "42.75",
                2L,
                "Publix",
                "Weekly groceries");

        ValidatedTransactionImportRow income = row(
                3,
                AUGUST,
                TransactionType.INCOME,
                "1000.00",
                8L,
                "Employer",
                "Paycheck");

        writer.write(new Chunk<>(List.of(expense, income)));

        verify(financialAccountRepository).findByIdAndUserIdForUpdate(ACCOUNT_ID, USER_ID);
        verify(account).debit(new BigDecimal("42.75"));
        verify(account).credit(new BigDecimal("1000.00"));

        ArgumentCaptor<Iterable<FinancialTransaction>> transactionCaptor =
                ArgumentCaptor.forClass(Iterable.class);

        verify(financialTransactionRepository).saveAllAndFlush(transactionCaptor.capture());

        List<FinancialTransaction> transactions = StreamSupport
                .stream(transactionCaptor.getValue().spliterator(), false)
                .toList();

        assertThat(transactions).hasSize(2);

        FinancialTransaction expenseTransaction = transactions.get(0);

        assertThat(expenseTransaction.getImportId()).isEqualTo(IMPORT_ID);
        assertThat(expenseTransaction.getImportRowNumber()).isEqualTo(2);
        assertThat(expenseTransaction.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(expenseTransaction.getCategoryId()).isEqualTo(2L);
        assertThat(expenseTransaction.getTransactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(expenseTransaction.getAmount()).isEqualByComparingTo("42.75");
        assertThat(expenseTransaction.getMerchant()).isEqualTo("Publix");
        assertThat(expenseTransaction.getDescription()).isEqualTo("Weekly groceries");
        assertThat(expenseTransaction.getTransactionDate()).isEqualTo(AUGUST);
        assertThat(expenseTransaction.getProcessingStatus()).isEqualTo(ProcessingStatus.PROCESSED);
        assertThat(expenseTransaction.getSource()).isEqualTo(TransactionSource.IMPORT);
        assertThat(expenseTransaction.isManualCategoryOverride()).isFalse();

        FinancialTransaction incomeTransaction = transactions.get(1);

        assertThat(incomeTransaction.getImportRowNumber()).isEqualTo(3);
        assertThat(incomeTransaction.getCategoryId()).isEqualTo(8L);
        assertThat(incomeTransaction.getTransactionType()).isEqualTo(TransactionType.INCOME);
        assertThat(incomeTransaction.getAmount()).isEqualByComparingTo("1000.00");

        verify(budgetEvaluationService).evaluate(USER_ID, 2L, AUGUST);

        verify(notificationService).createIfRequired(
                eq(USER_ID),
                eq(2L),
                nullable(Long.class),
                eq(AUGUST),
                eq(evaluation));
    }

    @Test
    void writeEvaluatesEachAffectedBudgetOnceInDeterministicOrder() {
        FinancialAccount account = activeAccount();

        when(budgetEvaluationService.evaluate(USER_ID, 2L, AUGUST))
                .thenReturn(Optional.empty());
        when(budgetEvaluationService.evaluate(USER_ID, 2L, SEPTEMBER))
                .thenReturn(Optional.empty());
        when(budgetEvaluationService.evaluate(USER_ID, 5L, AUGUST))
                .thenReturn(Optional.empty());

        Chunk<ValidatedTransactionImportRow> chunk = new Chunk<>(List.of(
                row(2, AUGUST, TransactionType.EXPENSE, "10.00", 5L, "Cinema", null),
                row(3, SEPTEMBER, TransactionType.EXPENSE, "20.00", 2L, "Publix", null),
                row(4, AUGUST, TransactionType.EXPENSE, "30.00", 2L, "Market", null),
                row(5, AUGUST, TransactionType.EXPENSE, "40.00", 5L, "Theater", null)
        ));

        writer.write(chunk);

        InOrder budgetOrder = inOrder(budgetEvaluationService);

        budgetOrder.verify(budgetEvaluationService).evaluate(USER_ID, 2L, AUGUST);
        budgetOrder.verify(budgetEvaluationService).evaluate(USER_ID, 2L, SEPTEMBER);
        budgetOrder.verify(budgetEvaluationService).evaluate(USER_ID, 5L, AUGUST);

        verifyNoMoreInteractions(budgetEvaluationService);
        verifyNoInteractions(notificationService);

        verify(account).debit(new BigDecimal("10.00"));
        verify(account).debit(new BigDecimal("20.00"));
        verify(account).debit(new BigDecimal("30.00"));
        verify(account).debit(new BigDecimal("40.00"));
    }

    @Test
    void writeSkipsBudgetEvaluationForIncomeOnlyChunk() {
        FinancialAccount account = activeAccount();

        Chunk<ValidatedTransactionImportRow> chunk = new Chunk<>(List.of(
                row(
                        2,
                        AUGUST,
                        TransactionType.INCOME,
                        "1000.00",
                        8L,
                        "Employer",
                        "Paycheck")
        ));

        writer.write(chunk);

        verify(account).credit(new BigDecimal("1000.00"));
        verify(financialTransactionRepository).saveAllAndFlush(any());
        verifyNoInteractions(budgetEvaluationService, notificationService);
    }

    @Test
    void writeDoesNothingForEmptyChunk() {
        writer.write(new Chunk<>());

        verifyNoInteractions(
                financialAccountRepository,
                financialTransactionRepository,
                budgetEvaluationService,
                notificationService);
    }

    @Test
    void writeRejectsMissingOwnedAccount() {
        when(financialAccountRepository.findByIdAndUserIdForUpdate(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.empty());

        Chunk<ValidatedTransactionImportRow> chunk = new Chunk<>(List.of(
                row(
                        2,
                        AUGUST,
                        TransactionType.EXPENSE,
                        "42.75",
                        2L,
                        "Publix",
                        "Groceries")
        ));

        assertThatThrownBy(() -> writer.write(chunk))
                .isInstanceOf(TransactionImportAccountUnavailableException.class)
                .hasMessage("Financial account 22 is unavailable for transaction import by user 9");

        verifyNoInteractions(
                financialTransactionRepository,
                budgetEvaluationService,
                notificationService);
    }

    @Test
    void writeRejectsClosedAccount() {
        FinancialAccount account = mock(FinancialAccount.class);

        when(financialAccountRepository.findByIdAndUserIdForUpdate(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(account));
        when(account.getStatus()).thenReturn(AccountStatus.CLOSED);

        Chunk<ValidatedTransactionImportRow> chunk = new Chunk<>(List.of(
                row(
                        2,
                        AUGUST,
                        TransactionType.EXPENSE,
                        "42.75",
                        2L,
                        "Publix",
                        "Groceries")
        ));

        assertThatThrownBy(() -> writer.write(chunk))
                .isInstanceOf(TransactionImportAccountUnavailableException.class)
                .hasMessage("Financial account 22 is unavailable for transaction import by user 9");

        verifyNoInteractions(
                financialTransactionRepository,
                budgetEvaluationService,
                notificationService);
    }

    private FinancialAccount activeAccount() {
        FinancialAccount account = mock(FinancialAccount.class);

        when(financialAccountRepository.findByIdAndUserIdForUpdate(ACCOUNT_ID, USER_ID))
                .thenReturn(Optional.of(account));
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);

        return account;
    }

    private ValidatedTransactionImportRow row(int rowNumber, LocalDate transactionDate,
                                              TransactionType transactionType, String amount,
                                              Long categoryId, String merchant, String description) {
        return new ValidatedTransactionImportRow(
                rowNumber,
                transactionDate,
                transactionType,
                new BigDecimal(amount),
                merchant,
                description,
                categoryId);
    }
}