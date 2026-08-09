package com.fintrack.workerservice.transaction.repository;

import com.fintrack.workerservice.transaction.entity.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

    @Query(value = """
            SELECT ft.*
            FROM financial_transaction ft
            JOIN financial_account fa
              ON fa.id = ft.account_id
            WHERE ft.id = :transactionId
              AND fa.user_id = :userId
            """, nativeQuery = true)
    Optional<FinancialTransaction> findByIdAndUserId(@Param("transactionId") Long transactionId,
                                                     @Param("userId") Long userId);

    @Query(value = """
            SELECT COALESCE(SUM(ft.amount), 0)
            FROM financial_transaction ft
            JOIN financial_account fa
              ON fa.id = ft.account_id
            WHERE fa.user_id = :userId
              AND ft.category_id = :categoryId
              AND ft.transaction_type = 'EXPENSE'
              AND ft.processing_status = 'PROCESSED'
              AND ft.transaction_date >= :monthStart
              AND ft.transaction_date < :nextMonthStart
            """, nativeQuery = true)
    BigDecimal sumProcessedExpenses(@Param("userId") Long userId,
                                    @Param("categoryId") Long categoryId,
                                    @Param("monthStart") LocalDate monthStart,
                                    @Param("nextMonthStart") LocalDate nextMonthStart);
}