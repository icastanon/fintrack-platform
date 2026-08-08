package com.fintrack.workerservice.category.repository;

import com.fintrack.workerservice.category.entity.CategorizationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, Long> {

    List<CategorizationRule> findAllByActiveTrueOrderByPriorityAscIdAsc();
}