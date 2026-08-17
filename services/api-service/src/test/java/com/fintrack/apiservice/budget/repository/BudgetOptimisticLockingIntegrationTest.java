package com.fintrack.apiservice.budget.repository;

import com.fintrack.apiservice.budget.entity.Budget;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Testcontainers
@DataJpaTest(
        showSql = false,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false"
        }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BudgetOptimisticLockingIntegrationTest {

    private static final Long USER_ID = 11L;
    private static final Long CATEGORY_ID = 21L;
    private static final Long BUDGET_ID = 31L;
    private static final LocalDate BUDGET_MONTH = LocalDate.of(2026, 8, 1);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE budget,
                               category,
                               fintrack_user
                CASCADE
                """);

        insertUser();
        insertCategory();
        insertBudget();
    }

    @Test
    void staleBudgetUpdateCannotOverwriteNewerCommittedUpdate() {
        EntityManager firstEntityManager = entityManagerFactory.createEntityManager();
        EntityManager secondEntityManager = entityManagerFactory.createEntityManager();

        EntityTransaction firstTransaction = firstEntityManager.getTransaction();
        EntityTransaction secondTransaction = secondEntityManager.getTransaction();

        try {
            firstTransaction.begin();
            secondTransaction.begin();

            Budget firstBudget = firstEntityManager.find(Budget.class, BUDGET_ID);
            Budget staleBudget = secondEntityManager.find(Budget.class, BUDGET_ID);

            assertThat(firstBudget.getVersion()).isZero();
            assertThat(staleBudget.getVersion()).isZero();

            firstBudget.update(new BigDecimal("400.00"), 85);
            firstTransaction.commit();

            assertThat(firstBudget.getVersion()).isEqualTo(1L);

            staleBudget.update(new BigDecimal("500.00"), 90);

            Throwable failure = catchThrowable(secondTransaction::commit);

            assertThat(failure).isNotNull();
            assertThat(containsCause(failure, OptimisticLockException.class)).isTrue();
        } finally {
            rollbackIfActive(firstTransaction);
            rollbackIfActive(secondTransaction);
            firstEntityManager.close();
            secondEntityManager.close();
        }

        assertPersistedFirstUpdate();
    }

    private void assertPersistedFirstUpdate() {
        EntityManager verificationEntityManager = entityManagerFactory.createEntityManager();

        try {
            Budget persistedBudget = verificationEntityManager.find(Budget.class, BUDGET_ID);

            assertThat(persistedBudget.getAmount()).isEqualByComparingTo("400.00");
            assertThat(persistedBudget.getWarningThresholdPercentage()).isEqualTo(85);
            assertThat(persistedBudget.getVersion()).isEqualTo(1L);
        } finally {
            verificationEntityManager.close();
        }
    }

    private boolean containsCause(Throwable failure, Class<? extends Throwable> expectedType) {
        Throwable current = failure;

        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private void rollbackIfActive(EntityTransaction transaction) {
        if (transaction.isActive()) {
            transaction.rollback();
        }
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
                USER_ID,
                "optimistic-lock-user",
                "optimistic-lock-user@example.com",
                "password-hash",
                "USD",
                "USER"
        );
    }

    private void insertCategory() {
        jdbcTemplate.update("""
                INSERT INTO category (
                    id,
                    name
                )
                VALUES (?, ?)
                """,
                CATEGORY_ID,
                "Groceries"
        );
    }

    private void insertBudget() {
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
                BUDGET_ID,
                USER_ID,
                CATEGORY_ID,
                BUDGET_MONTH,
                new BigDecimal("300.00"),
                80
        );
    }
}