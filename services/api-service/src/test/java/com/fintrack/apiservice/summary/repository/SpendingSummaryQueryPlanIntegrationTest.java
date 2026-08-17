package com.fintrack.apiservice.summary.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest(
        showSql = false,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create",
                "spring.flyway.enabled=false"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SpendingSummaryQueryPlanIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpendingSummaryQueryPlanIntegrationTest.class);

    private static final int TRANSACTION_COUNT = 100_000;
    private static final String CANDIDATE_INDEX = "idx_financial_transaction_budget_usage";

    private static final String BUDGET_USAGE_EXPLAIN_SQL = """
            EXPLAIN (ANALYZE, BUFFERS)
            SELECT
                budget.id,
                category.id,
                category.name,
                budget.amount,
                budget.warning_threshold_percentage,
                COALESCE(SUM(transaction.amount), 0) AS spent_amount
            FROM budget
            JOIN category
                ON category.id = budget.category_id
            LEFT JOIN financial_account account
                ON account.user_id = budget.user_id
            LEFT JOIN financial_transaction transaction
                ON transaction.account_id = account.id
               AND transaction.category_id = budget.category_id
               AND transaction.transaction_type = 'EXPENSE'
               AND transaction.processing_status = 'PROCESSED'
               AND transaction.transaction_date >= DATE '2026-01-01'
               AND transaction.transaction_date < DATE '2026-02-01'
            WHERE budget.user_id = 11
              AND budget.budget_month = DATE '2026-01-01'
            GROUP BY
                budget.id,
                category.id,
                category.name,
                budget.amount,
                budget.warning_threshold_percentage
            ORDER BY category.name ASC, budget.id ASC
            """;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP INDEX IF EXISTS " + CANDIDATE_INDEX);

        jdbcTemplate.execute("""
                TRUNCATE TABLE financial_transaction,
                               budget,
                               financial_account,
                               category,
                               fintrack_user
                CASCADE
                """);

        createBaselineIndexes();
        insertUser();
        insertCategories();
        insertAccounts();
        insertBudgets();
        insertTransactions();

        jdbcTemplate.execute("VACUUM ANALYZE financial_transaction");
        jdbcTemplate.execute("ANALYZE financial_account");
        jdbcTemplate.execute("ANALYZE budget");
        jdbcTemplate.execute("ANALYZE category");
    }

    @Test
    void candidateIndexImprovesBudgetUsageQueryPlanAtRepresentativeVolume() {
        List<String> baselinePlan = explainBudgetUsage();

        assertThat(baselinePlan)
                .noneMatch(line -> line.contains(CANDIDATE_INDEX));

        createCandidateIndex();
        jdbcTemplate.execute("ANALYZE financial_transaction");

        List<String> optimizedPlan = explainBudgetUsage();

        assertThat(optimizedPlan)
                .anyMatch(line -> line.contains(CANDIDATE_INDEX));

        logPlan("BUDGET USAGE PLAN BEFORE CANDIDATE INDEX", baselinePlan);
        logPlan("BUDGET USAGE PLAN AFTER CANDIDATE INDEX", optimizedPlan);
    }

    private List<String> explainBudgetUsage() {
        return jdbcTemplate.queryForList(BUDGET_USAGE_EXPLAIN_SQL, String.class);
    }

    private void createBaselineIndexes() {
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_financial_account_user_status
                    ON financial_account (user_id, status)
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_budget_user_month
                    ON budget (user_id, budget_month DESC, id DESC)
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_financial_transaction_account_date
                    ON financial_transaction (account_id, transaction_date DESC, id DESC)
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_financial_transaction_category_date
                    ON financial_transaction (category_id, transaction_date DESC, id DESC)
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_financial_transaction_status
                    ON financial_transaction (processing_status, id)
                """);
    }

    private void createCandidateIndex() {
        jdbcTemplate.execute("""
                CREATE INDEX idx_financial_transaction_budget_usage
                    ON financial_transaction (
                        account_id,
                        category_id,
                        transaction_date
                    )
                    INCLUDE (amount)
                    WHERE transaction_type = 'EXPENSE'
                      AND processing_status = 'PROCESSED'
                """);
    }

    private void insertUser() {
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
                11L,
                "query-plan-user",
                "query-plan-user@example.com",
                "password-hash",
                "USD",
                "USER"
        );
    }

    private void insertCategories() {
        jdbcTemplate.update("""
                INSERT INTO category (
                    id,
                    name
                )
                SELECT
                    200 + number,
                    'Category ' || number
                FROM generate_series(1, 9) AS generated(number)
                """);
    }

    private void insertAccounts() {
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
                SELECT
                    100 + number,
                    11,
                    'Account ' || number,
                    'CHECKING',
                    0,
                    0,
                    'ACTIVE',
                    0,
                    clock_timestamp(),
                    clock_timestamp()
                FROM generate_series(1, 5) AS generated(number)
                """);
    }

    private void insertBudgets() {
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
                SELECT
                    300 + number,
                    11,
                    200 + number,
                    DATE '2026-01-01',
                    1000,
                    80,
                    0,
                    clock_timestamp(),
                    clock_timestamp()
                FROM generate_series(1, 9) AS generated(number)
                """);
    }

    private void insertTransactions() {
        jdbcTemplate.update("""
                INSERT INTO financial_transaction (
                    account_id,
                    category_id,
                    transaction_type,
                    amount,
                    merchant,
                    description,
                    transaction_date,
                    processing_status,
                    source,
                    manual_category_override,
                    version,
                    created_at,
                    updated_at
                )
                SELECT
                    101 + ((number - 1) % 5),
                    201 + ((number - 1) % 9),
                    CASE
                        WHEN number % 6 = 0 THEN 'INCOME'
                        ELSE 'EXPENSE'
                    END,
                    ((number % 200) + 1)::NUMERIC(19, 2),
                    'Merchant ' || (number % 100),
                    'Query plan fixture',
                    DATE '2025-01-01' + ((number - 1) % 730),
                    CASE
                        WHEN number % 11 = 0 THEN 'PENDING'
                        ELSE 'PROCESSED'
                    END,
                    'MANUAL',
                    FALSE,
                    0,
                    clock_timestamp(),
                    clock_timestamp()
                FROM generate_series(1, ?) AS generated(number)
                """,
                TRANSACTION_COUNT
        );
    }

    private void logPlan(String heading, List<String> plan) {
        LOGGER.info("{}\n{}", heading, String.join("\n", plan));
    }
}