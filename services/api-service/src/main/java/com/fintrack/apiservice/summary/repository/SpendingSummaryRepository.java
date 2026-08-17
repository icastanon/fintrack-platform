package com.fintrack.apiservice.summary.repository;

import com.fintrack.apiservice.summary.projection.AccountSpendingProjection;
import com.fintrack.apiservice.summary.projection.BudgetUsageProjection;
import com.fintrack.apiservice.summary.projection.CategorySpendingProjection;
import com.fintrack.apiservice.summary.projection.MonthlyCashFlowProjection;
import com.fintrack.apiservice.transaction.entity.FinancialTransaction;
import com.fintrack.apiservice.transaction.entity.ProcessingStatus;
import com.fintrack.apiservice.transaction.entity.TransactionType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SpendingSummaryRepository extends Repository<FinancialTransaction, Long> {

    @Query("""
            SELECT
                COALESCE(
                    SUM(
                        CASE
                            WHEN transaction.transactionType = :incomeType
                            THEN transaction.amount
                            ELSE 0
                        END
                    ),
                    0
                ) AS income,
                COALESCE(
                    SUM(
                        CASE
                            WHEN transaction.transactionType = :expenseType
                            THEN transaction.amount
                            ELSE 0
                        END
                    ),
                    0
                ) AS expenses
            FROM FinancialTransaction transaction
            JOIN transaction.account account
            WHERE account.user.id = :userId
              AND transaction.processingStatus = :processingStatus
              AND transaction.transactionDate >= :startDate
              AND transaction.transactionDate < :endDateExclusive
            """)
    MonthlyCashFlowProjection summarizeCashFlow(@Param("userId") Long userId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDateExclusive") LocalDate endDateExclusive,
                                                @Param("incomeType") TransactionType incomeType,
                                                @Param("expenseType") TransactionType expenseType,
                                                @Param("processingStatus") ProcessingStatus processingStatus);

    @Query("""
            SELECT
                category.id AS categoryId,
                category.name AS categoryName,
                SUM(transaction.amount) AS spentAmount
            FROM FinancialTransaction transaction
            JOIN transaction.account account
            JOIN transaction.category category
            WHERE account.user.id = :userId
              AND transaction.transactionType = :expenseType
              AND transaction.processingStatus = :processingStatus
              AND transaction.transactionDate >= :startDate
              AND transaction.transactionDate < :endDateExclusive
            GROUP BY category.id, category.name
            ORDER BY SUM(transaction.amount) DESC, category.id ASC
            """)
    List<CategorySpendingProjection> summarizeSpendingByCategory(@Param("userId") Long userId,
                                                                 @Param("startDate") LocalDate startDate,
                                                                 @Param("endDateExclusive") LocalDate endDateExclusive,
                                                                 @Param("expenseType") TransactionType expenseType,
                                                                 @Param("processingStatus") ProcessingStatus processingStatus);

    @Query("""
        SELECT
            account.id AS accountId,
            account.name AS accountName,
            SUM(transaction.amount) AS spentAmount
        FROM FinancialTransaction transaction
        JOIN transaction.account account
        WHERE account.user.id = :userId
          AND transaction.transactionType = :expenseType
          AND transaction.processingStatus = :processingStatus
          AND transaction.transactionDate >= :startDate
          AND transaction.transactionDate < :endDateExclusive
        GROUP BY account.id, account.name
        ORDER BY SUM(transaction.amount) DESC, account.id ASC
        """)
    List<AccountSpendingProjection> summarizeSpendingByAccount(@Param("userId") Long userId,
                                                               @Param("startDate") LocalDate startDate,
                                                               @Param("endDateExclusive") LocalDate endDateExclusive,
                                                               @Param("expenseType") TransactionType expenseType,
                                                               @Param("processingStatus") ProcessingStatus processingStatus);

    @Query(value = """
        SELECT
            budget.id AS "budgetId",
            category.id AS "categoryId",
            category.name AS "categoryName",
            budget.amount AS "budgetAmount",
            budget.warning_threshold_percentage AS "warningThresholdPercentage",
            COALESCE(SUM(transaction.amount), 0) AS "spentAmount"
        FROM budget
        JOIN category
          ON category.id = budget.category_id
        LEFT JOIN financial_account account
          ON account.user_id = budget.user_id
        LEFT JOIN financial_transaction transaction
          ON transaction.account_id = account.id
         AND transaction.category_id = budget.category_id
         AND transaction.transaction_type = :expenseType
         AND transaction.processing_status = :processingStatus
         AND transaction.transaction_date >= :monthStart
         AND transaction.transaction_date < :endDateExclusive
        WHERE budget.user_id = :userId
          AND budget.budget_month = :monthStart
        GROUP BY
            budget.id,
            category.id,
            category.name,
            budget.amount,
            budget.warning_threshold_percentage
        ORDER BY category.name ASC, budget.id ASC
        """, nativeQuery = true)
    List<BudgetUsageProjection> summarizeBudgetUsage(@Param("userId") Long userId,
                                                     @Param("monthStart") LocalDate monthStart,
                                                     @Param("endDateExclusive") LocalDate endDateExclusive,
                                                     @Param("expenseType") String expenseType,
                                                     @Param("processingStatus") String processingStatus);
}