package com.fintrack.workerservice.budget.repository;

import com.fintrack.workerservice.budget.entity.Budget;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT budget
            FROM Budget budget
            WHERE budget.userId = :userId
              AND budget.categoryId = :categoryId
              AND budget.budgetMonth = :budgetMonth
            """)
    Optional<Budget> findForEvaluation(@Param("userId") Long userId,
                                       @Param("categoryId") Long categoryId,
                                       @Param("budgetMonth") LocalDate budgetMonth);
}