package com.fintrack.apiservice.transactionimport.repository;

import com.fintrack.apiservice.transactionimport.entity.TransactionImport;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionImportRepository extends JpaRepository<TransactionImport, Long> {

    @EntityGraph(attributePaths = "account")
    Optional<TransactionImport> findByIdAndAccountUserId(Long importId, Long userId);
}