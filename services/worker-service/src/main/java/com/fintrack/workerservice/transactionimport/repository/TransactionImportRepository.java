package com.fintrack.workerservice.transactionimport.repository;

import com.fintrack.workerservice.transactionimport.entity.TransactionImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionImportRepository extends JpaRepository<TransactionImport, Long> {

    @Query(value = """
            SELECT ti.*
            FROM transaction_import ti
            JOIN financial_account fa
              ON fa.id = ti.account_id
            WHERE ti.id = :importId
              AND ti.account_id = :accountId
              AND fa.user_id = :userId
            """, nativeQuery = true)
    Optional<TransactionImport> findByIdAndAccountIdAndUserId(@Param("importId") Long importId,
                                                              @Param("accountId") Long accountId,
                                                              @Param("userId") Long userId);
}