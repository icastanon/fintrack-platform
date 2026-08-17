package com.fintrack.apiservice.summary.repository;

import com.fintrack.apiservice.summary.projection.AccountSpendingProjection;
import com.fintrack.apiservice.summary.projection.BudgetUsageProjection;
import com.fintrack.apiservice.summary.projection.CategorySpendingProjection;
import com.fintrack.apiservice.summary.projection.MonthlyCashFlowProjection;
import com.fintrack.apiservice.transaction.entity.ProcessingStatus;
import com.fintrack.apiservice.transaction.entity.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest(
        showSql = false,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SpendingSummaryRepositoryIntegrationTest {

    private static final Long USER_ID = 11L;
    private static final Long OTHER_USER_ID = 12L;
    private static final Long EMPTY_USER_ID = 13L;

    private static final Long ACCOUNT_ID = 21L;
    private static final Long OTHER_ACCOUNT_ID = 22L;
    private static final Long SECOND_ACCOUNT_ID = 23L;

    private static final Long GROCERIES_CATEGORY_ID = 31L;
    private static final Long RESTAURANTS_CATEGORY_ID = 32L;
    private static final Long UTILITIES_CATEGORY_ID = 33L;

    private static final Long GROCERIES_BUDGET_ID = 41L;
    private static final Long RESTAURANTS_BUDGET_ID = 42L;
    private static final Long UTILITIES_BUDGET_ID = 43L;

    private static final LocalDate AUGUST_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate SEPTEMBER_START = LocalDate.of(2026, 9, 1);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SpendingSummaryRepository spendingSummaryRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE budget,
                               financial_transaction,
                               financial_account,
                               category,
                               fintrack_user
                CASCADE
                """);

        insertUser(USER_ID, "summary-user");
        insertUser(OTHER_USER_ID, "other-user");
        insertUser(EMPTY_USER_ID, "empty-user");

        insertAccount(ACCOUNT_ID, USER_ID, "Primary checking");
        insertAccount(SECOND_ACCOUNT_ID, USER_ID, "Travel card");
        insertAccount(OTHER_ACCOUNT_ID, OTHER_USER_ID, "Other checking");

        insertCategory(GROCERIES_CATEGORY_ID, "Groceries");
        insertCategory(RESTAURANTS_CATEGORY_ID, "Restaurants");
        insertCategory(UTILITIES_CATEGORY_ID, "Utilities");

        insertBudget(GROCERIES_BUDGET_ID, USER_ID, GROCERIES_CATEGORY_ID, AUGUST_START, "300.00", 80);
        insertBudget(RESTAURANTS_BUDGET_ID, USER_ID, RESTAURANTS_CATEGORY_ID, AUGUST_START, "50.00", 80);
        insertBudget(UTILITIES_BUDGET_ID, USER_ID, UTILITIES_CATEGORY_ID, AUGUST_START, "100.00", 80);

        insertBudget(44L, OTHER_USER_ID, GROCERIES_CATEGORY_ID, AUGUST_START, "5000.00", 80);
        insertBudget(45L, USER_ID, GROCERIES_CATEGORY_ID, SEPTEMBER_START, "400.00", 80);

        insertTransaction(1L, ACCOUNT_ID, null, "INCOME", "1000.00", LocalDate.of(2026, 8, 1), "PROCESSED");

        insertTransaction(2L, ACCOUNT_ID, GROCERIES_CATEGORY_ID, "EXPENSE", "200.00", LocalDate.of(2026, 8, 10), "PROCESSED");
        insertTransaction(3L, SECOND_ACCOUNT_ID, GROCERIES_CATEGORY_ID, "EXPENSE", "50.00", LocalDate.of(2026, 8, 31), "PROCESSED");
        insertTransaction(4L, ACCOUNT_ID, RESTAURANTS_CATEGORY_ID, "EXPENSE", "50.00", LocalDate.of(2026, 8, 20), "PROCESSED");

        insertTransaction(5L, ACCOUNT_ID, GROCERIES_CATEGORY_ID, "EXPENSE", "500.00", LocalDate.of(2026, 8, 15), "PENDING");
        insertTransaction(6L, ACCOUNT_ID, RESTAURANTS_CATEGORY_ID, "EXPENSE", "75.00", LocalDate.of(2026, 8, 15), "FAILED");

        insertTransaction(7L, ACCOUNT_ID, GROCERIES_CATEGORY_ID, "EXPENSE", "200.00", LocalDate.of(2026, 7, 31), "PROCESSED");
        insertTransaction(8L, SECOND_ACCOUNT_ID, RESTAURANTS_CATEGORY_ID, "EXPENSE", "300.00", LocalDate.of(2026, 9, 1), "PROCESSED");

        insertTransaction(9L, OTHER_ACCOUNT_ID, null, "INCOME", "9000.00", LocalDate.of(2026, 8, 10), "PROCESSED");
        insertTransaction(10L, OTHER_ACCOUNT_ID, GROCERIES_CATEGORY_ID, "EXPENSE", "4000.00", LocalDate.of(2026, 8, 11), "PROCESSED");
    }

    @Test
    void summarizeCashFlowAggregatesOnlyOwnedProcessedTransactionsWithinDateRange() {
        MonthlyCashFlowProjection projection = spendingSummaryRepository.summarizeCashFlow(
                USER_ID,
                AUGUST_START,
                SEPTEMBER_START,
                TransactionType.INCOME,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        );

        assertThat(projection).isNotNull();
        assertThat(projection.getIncome()).isEqualByComparingTo("1000.00");
        assertThat(projection.getExpenses()).isEqualByComparingTo("300.00");
    }

    @Test
    void summarizeCashFlowReturnsZerosWhenUserHasNoTransactions() {
        MonthlyCashFlowProjection projection = spendingSummaryRepository.summarizeCashFlow(
                EMPTY_USER_ID,
                AUGUST_START,
                SEPTEMBER_START,
                TransactionType.INCOME,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        );

        assertThat(projection).isNotNull();
        assertThat(projection.getIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(projection.getExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void summarizeSpendingByCategoryGroupsOwnedProcessedExpensesAndOrdersByAmount() {
        List<CategorySpendingProjection> results = spendingSummaryRepository.summarizeSpendingByCategory(
                USER_ID,
                AUGUST_START,
                SEPTEMBER_START,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        );

        assertThat(results).hasSize(2);

        assertThat(results.get(0).getCategoryId()).isEqualTo(GROCERIES_CATEGORY_ID);
        assertThat(results.get(0).getCategoryName()).isEqualTo("Groceries");
        assertThat(results.get(0).getSpentAmount()).isEqualByComparingTo("250.00");

        assertThat(results.get(1).getCategoryId()).isEqualTo(RESTAURANTS_CATEGORY_ID);
        assertThat(results.get(1).getCategoryName()).isEqualTo("Restaurants");
        assertThat(results.get(1).getSpentAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void summarizeSpendingByCategoryReturnsEmptyListWhenUserHasNoExpenses() {
        List<CategorySpendingProjection> results = spendingSummaryRepository.summarizeSpendingByCategory(
                EMPTY_USER_ID,
                AUGUST_START,
                SEPTEMBER_START,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        );

        assertThat(results).isEmpty();
    }

    @Test
    void summarizeSpendingByAccountGroupsOwnedProcessedExpensesAndOrdersByAmount() {
        List<AccountSpendingProjection> results = spendingSummaryRepository.summarizeSpendingByAccount(
                USER_ID,
                AUGUST_START,
                SEPTEMBER_START,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        );

        assertThat(results).hasSize(2);

        assertThat(results.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(results.get(0).getAccountName()).isEqualTo("Primary checking");
        assertThat(results.get(0).getSpentAmount()).isEqualByComparingTo("250.00");

        assertThat(results.get(1).getAccountId()).isEqualTo(SECOND_ACCOUNT_ID);
        assertThat(results.get(1).getAccountName()).isEqualTo("Travel card");
        assertThat(results.get(1).getSpentAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void summarizeSpendingByAccountReturnsEmptyListWhenUserHasNoExpenses() {
        List<AccountSpendingProjection> results = spendingSummaryRepository.summarizeSpendingByAccount(
                EMPTY_USER_ID,
                AUGUST_START,
                SEPTEMBER_START,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        );

        assertThat(results).isEmpty();
    }

    @Test
    void summarizeBudgetUsageIncludesOwnedBudgetsAndZeroSpendingBudget() {
        List<BudgetUsageProjection> results = spendingSummaryRepository.summarizeBudgetUsage(
                USER_ID,
                AUGUST_START,
                SEPTEMBER_START,
                TransactionType.EXPENSE.name(),
                ProcessingStatus.PROCESSED.name()
        );

        assertThat(results).hasSize(3);

        assertThat(results.get(0).getBudgetId()).isEqualTo(GROCERIES_BUDGET_ID);
        assertThat(results.get(0).getCategoryId()).isEqualTo(GROCERIES_CATEGORY_ID);
        assertThat(results.get(0).getCategoryName()).isEqualTo("Groceries");
        assertThat(results.get(0).getBudgetAmount()).isEqualByComparingTo("300.00");
        assertThat(results.get(0).getWarningThresholdPercentage()).isEqualTo(80);
        assertThat(results.get(0).getSpentAmount()).isEqualByComparingTo("250.00");

        assertThat(results.get(1).getBudgetId()).isEqualTo(RESTAURANTS_BUDGET_ID);
        assertThat(results.get(1).getCategoryId()).isEqualTo(RESTAURANTS_CATEGORY_ID);
        assertThat(results.get(1).getCategoryName()).isEqualTo("Restaurants");
        assertThat(results.get(1).getBudgetAmount()).isEqualByComparingTo("50.00");
        assertThat(results.get(1).getWarningThresholdPercentage()).isEqualTo(80);
        assertThat(results.get(1).getSpentAmount()).isEqualByComparingTo("50.00");

        assertThat(results.get(2).getBudgetId()).isEqualTo(UTILITIES_BUDGET_ID);
        assertThat(results.get(2).getCategoryId()).isEqualTo(UTILITIES_CATEGORY_ID);
        assertThat(results.get(2).getCategoryName()).isEqualTo("Utilities");
        assertThat(results.get(2).getBudgetAmount()).isEqualByComparingTo("100.00");
        assertThat(results.get(2).getWarningThresholdPercentage()).isEqualTo(80);
        assertThat(results.get(2).getSpentAmount()).isEqualByComparingTo("0.00");
    }

    private void insertUser(Long userId, String username) {
        jdbcTemplate.update("""
                INSERT INTO fintrack_user (
                    id,
                    username,
                    email,
                    password_hash,
                    currency,
                    user_role,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, clock_timestamp())
                """,
                userId,
                username,
                username + "@example.com",
                "password-hash",
                "USD",
                "USER");
    }

    private void insertAccount(Long accountId, Long userId, String accountName) {
        jdbcTemplate.update("""
                INSERT INTO financial_account (
                    id,
                    user_id,
                    account_name,
                    account_type,
                    opening_balance,
                    current_balance,
                    status,
                    version,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?,
                    0.00, 0.00,
                    ?, 0,
                    clock_timestamp(),
                    clock_timestamp()
                )
                """,
                accountId,
                userId,
                accountName,
                "CHECKING",
                "ACTIVE");
    }

    private void insertCategory(Long categoryId, String categoryName) {
        jdbcTemplate.update("""
                INSERT INTO category (
                    id,
                    name
                )
                VALUES (?, ?)
                """,
                categoryId,
                categoryName);
    }

    private void insertBudget(Long budgetId,
                              Long userId,
                              Long categoryId,
                              LocalDate budgetMonth,
                              String amount,
                              int warningThresholdPercentage) {
        jdbcTemplate.update("""
                INSERT INTO budget (
                    id,
                    user_id,
                    category_id,
                    budget_month,
                    amount,
                    warning_threshold_percentage,
                    version,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?,
                    0,
                    clock_timestamp(),
                    clock_timestamp()
                )
                """,
                budgetId,
                userId,
                categoryId,
                budgetMonth,
                new BigDecimal(amount),
                warningThresholdPercentage);
    }

    private void insertTransaction(Long transactionId,
                                   Long accountId,
                                   Long categoryId,
                                   String transactionType,
                                   String amount,
                                   LocalDate transactionDate,
                                   String processingStatus) {
        jdbcTemplate.update("""
                INSERT INTO financial_transaction (
                    id,
                    account_id,
                    category_id,
                    transaction_type,
                    amount,
                    transaction_date,
                    processing_status,
                    source,
                    manual_category_override,
                    version,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?,
                    FALSE, 0,
                    clock_timestamp(),
                    clock_timestamp()
                )
                """,
                transactionId,
                accountId,
                categoryId,
                transactionType,
                new BigDecimal(amount),
                transactionDate,
                processingStatus,
                "MANUAL");
    }
}