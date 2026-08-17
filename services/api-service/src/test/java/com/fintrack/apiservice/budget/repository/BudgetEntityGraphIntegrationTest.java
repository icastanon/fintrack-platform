package com.fintrack.apiservice.budget.repository;

import com.fintrack.apiservice.budget.entity.Budget;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitUtil;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
                "spring.flyway.enabled=false",
                "spring.jpa.properties.hibernate.generate_statistics=true"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BudgetEntityGraphIntegrationTest {

    private static final Long USER_ID = 11L;
    private static final LocalDate BUDGET_MONTH = LocalDate.of(2026, 8, 1);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private BudgetRepository budgetRepository;

    private Statistics statistics;
    private PersistenceUnitUtil persistenceUnitUtil;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE budget,
                               category,
                               fintrack_user
                CASCADE
                """);

        insertUser(USER_ID, "budget-user");

        insertCategory(21L, "Groceries");
        insertCategory(22L, "Restaurants");
        insertCategory(23L, "Utilities");

        insertBudget(31L, 21L, "300.00");
        insertBudget(32L, 22L, "150.00");
        insertBudget(33L, 23L, "200.00");

        entityManager.clear();

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
        statistics.clear();
    }

    @Test
    void entityGraphEliminatesCategoryNPlusOneQueries() {
        List<Budget> lazilyLoadedBudgets = entityManager.createQuery("""
                        SELECT budget
                        FROM Budget budget
                        WHERE budget.user.id = :userId
                          AND budget.budgetMonth = :budgetMonth
                        ORDER BY budget.id
                        """, Budget.class)
                .setParameter("userId", USER_ID)
                .setParameter("budgetMonth", BUDGET_MONTH)
                .getResultList();

        assertThat(lazilyLoadedBudgets).hasSize(3);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

        assertThat(lazilyLoadedBudgets)
                .allSatisfy(budget -> assertThat(persistenceUnitUtil.isLoaded(budget.getCategory())).isFalse());

        List<String> lazilyLoadedCategoryNames = lazilyLoadedBudgets.stream()
                .map(budget -> budget.getCategory().getName())
                .toList();

        long lazyStatementCount = statistics.getPrepareStatementCount();

        assertThat(lazilyLoadedCategoryNames)
                .containsExactly("Groceries", "Restaurants", "Utilities");
        assertThat(lazyStatementCount).isEqualTo(4);

        entityManager.clear();
        statistics.clear();

        Page<Budget> eagerlyLoadedPage = budgetRepository.findAllByUserIdAndOptionalMonth(
                USER_ID,
                BUDGET_MONTH,
                PageRequest.of(0, 10, Sort.by("id").ascending())
        );

        assertThat(eagerlyLoadedPage.getContent()).hasSize(3);

        long statementsAfterRepositoryQuery = statistics.getPrepareStatementCount();

        assertThat(statementsAfterRepositoryQuery).isBetween(1L, 2L);

        assertThat(eagerlyLoadedPage.getContent())
                .allSatisfy(budget -> assertThat(persistenceUnitUtil.isLoaded(budget.getCategory())).isTrue());

        List<String> eagerlyLoadedCategoryNames = eagerlyLoadedPage.getContent().stream()
                .map(budget -> budget.getCategory().getName())
                .toList();

        assertThat(eagerlyLoadedCategoryNames)
                .containsExactly("Groceries", "Restaurants", "Utilities");

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(statementsAfterRepositoryQuery);
        assertThat(statistics.getPrepareStatementCount()).isLessThan(lazyStatementCount);
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
                "USER"
        );
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
                categoryName
        );
    }

    private void insertBudget(Long budgetId, Long categoryId, String amount) {
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
            VALUES (?, ?, ?, ?, ?, ?, 0, clock_timestamp(), clock_timestamp())
            """,
                budgetId,
                USER_ID,
                categoryId,
                BUDGET_MONTH,
                new BigDecimal(amount),
                80
        );
    }
}