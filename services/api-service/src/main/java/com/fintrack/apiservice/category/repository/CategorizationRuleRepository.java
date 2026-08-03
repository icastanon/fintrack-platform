package com.fintrack.apiservice.category.repository;

import com.fintrack.apiservice.category.entity.CategorizationRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, Long> {

    @EntityGraph(attributePaths = "category")
    List<CategorizationRule> findAllByActiveTrueOrderByPriorityAscIdAsc();
}