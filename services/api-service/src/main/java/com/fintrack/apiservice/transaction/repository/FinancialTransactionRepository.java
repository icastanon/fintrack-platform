package com.fintrack.apiservice.transaction.repository;

import com.fintrack.apiservice.transaction.entity.FinancialTransaction;
import com.fintrack.apiservice.transaction.entity.ProcessingStatus;
import com.fintrack.apiservice.transaction.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {

    @EntityGraph(attributePaths = {"account", "category"})
    Optional<FinancialTransaction> findByIdAndAccountUserId(Long transactionId, Long userId);

    @EntityGraph(attributePaths = {"account", "category"})
    @Query("""
        SELECT ft
        FROM FinancialTransaction ft
        JOIN ft.account account
        LEFT JOIN ft.category category
        WHERE account.user.id = :userId
          AND (:accountId IS NULL OR account.id = :accountId)
          AND (:categoryId IS NULL OR category.id = :categoryId)
          AND (:transactionType IS NULL OR ft.transactionType = :transactionType)
          AND (:processingStatus IS NULL OR ft.processingStatus = :processingStatus)
          AND (:fromDate IS NULL OR ft.transactionDate >= :fromDate)
          AND (:toDate IS NULL OR ft.transactionDate <= :toDate)
        """)
    Page<FinancialTransaction> findAllByFilters(
            @Param("userId") Long userId,
            @Param("accountId") Long accountId,
            @Param("categoryId") Long categoryId,
            @Param("transactionType") TransactionType transactionType,
            @Param("processingStatus") ProcessingStatus processingStatus,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );
}