package com.fintrack.workerservice.account.repository;

import com.fintrack.workerservice.account.entity.FinancialAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT account
            FROM FinancialAccount account
            WHERE account.id = :accountId
              AND account.userId = :userId
            """)
    Optional<FinancialAccount> findByIdAndUserIdForUpdate(@Param("accountId") Long accountId,
                                                          @Param("userId") Long userId);
}