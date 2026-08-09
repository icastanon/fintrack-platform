package com.fintrack.apiservice.account.repository;

import com.fintrack.apiservice.account.entity.FinancialAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, Long> {

    @EntityGraph(attributePaths = "user")
    Page<FinancialAccount> findAllByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<FinancialAccount> findByIdAndUserId(Long accountId, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);
}