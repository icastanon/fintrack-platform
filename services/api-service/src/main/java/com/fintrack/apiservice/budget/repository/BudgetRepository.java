package com.fintrack.apiservice.budget.repository;

import com.fintrack.apiservice.budget.entity.Budget;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    boolean existsByUserIdAndCategoryIdAndBudgetMonth(Long userId, Long categoryId, LocalDate budgetMonth);

    @EntityGraph(attributePaths = "category")
    Optional<Budget> findByIdAndUserId(Long budgetId, Long userId);

    @EntityGraph(attributePaths = "category")
    @Query("""
        SELECT budget
        FROM Budget budget
        WHERE budget.user.id = :userId
          AND (:budgetMonth IS NULL OR budget.budgetMonth = :budgetMonth)
        """)
    Page<Budget> findAllByUserIdAndOptionalMonth(@Param("userId") Long userId, @Param("budgetMonth") LocalDate budgetMonth, Pageable pageable);
}