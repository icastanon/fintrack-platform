package com.fintrack.workerservice.transaction.repository;

import com.fintrack.workerservice.transaction.entity.FinancialTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}