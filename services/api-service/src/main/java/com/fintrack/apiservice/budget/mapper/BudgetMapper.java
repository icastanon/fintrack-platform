package com.fintrack.apiservice.budget.mapper;

import com.fintrack.apiservice.budget.dto.BudgetResponse;
import com.fintrack.apiservice.budget.entity.Budget;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
public class BudgetMapper {

    public BudgetResponse toResponse(Budget budget) {
        return new BudgetResponse(
                budget.getId(),
                budget.getCategory().getId(),
                budget.getCategory().getName(),
                YearMonth.from(budget.getBudgetMonth()),
                budget.getAmount(),
                budget.getWarningThresholdPercentage(),
                budget.getVersion(),
                budget.getCreatedAt(),
                budget.getUpdatedAt()
        );
    }
}