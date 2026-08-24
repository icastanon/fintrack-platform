package com.fintrack.apiservice.transaction.repository;

import com.fintrack.apiservice.transaction.entity.FinancialTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long>,
        JpaSpecificationExecutor<FinancialTransaction> {

    @EntityGraph(attributePaths = {"account", "category"})
    Optional<FinancialTransaction> findByIdAndAccountUserId(Long transactionId, Long userId);

    @Override
    @EntityGraph(attributePaths = {"account", "category"})
    Page<FinancialTransaction> findAll(Specification<FinancialTransaction> specification, Pageable pageable);
}