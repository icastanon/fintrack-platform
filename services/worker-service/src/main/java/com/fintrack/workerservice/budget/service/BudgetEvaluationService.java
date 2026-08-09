package com.fintrack.workerservice.budget.service;

import com.fintrack.workerservice.budget.entity.Budget;
import com.fintrack.workerservice.budget.model.BudgetEvaluationResult;
import com.fintrack.workerservice.budget.model.BudgetStatus;
import com.fintrack.workerservice.budget.repository.BudgetRepository;
import com.fintrack.workerservice.transaction.repository.FinancialTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

@Service
public class BudgetEvaluationService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final BudgetRepository budgetRepository;
    private final FinancialTransactionRepository financialTransactionRepository;

    public BudgetEvaluationService(BudgetRepository budgetRepository,
                                   FinancialTransactionRepository financialTransactionRepository) {
        this.budgetRepository = budgetRepository;
        this.financialTransactionRepository = financialTransactionRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<BudgetEvaluationResult> evaluate(Long userId, Long categoryId, LocalDate transactionDate) {
        LocalDate monthStart = transactionDate.withDayOfMonth(1);
        LocalDate nextMonthStart = monthStart.plusMonths(1);

        Optional<Budget> optionalBudget = budgetRepository.findForEvaluation(userId, categoryId, monthStart);

        if (optionalBudget.isEmpty()) {
            return Optional.empty();
        }

        Budget budget = optionalBudget.get();

        financialTransactionRepository.flush();

        BigDecimal spentAmount = financialTransactionRepository.sumProcessedExpenses(userId, categoryId, monthStart, nextMonthStart);
        BigDecimal usagePercentage = spentAmount.multiply(ONE_HUNDRED).divide(budget.getAmount(), 2, RoundingMode.HALF_UP);

        BudgetStatus status = determineStatus(spentAmount, budget.getAmount(), budget.getWarningThresholdPercentage());

        return Optional.of(
                new BudgetEvaluationResult(budget.getId(), budget.getAmount(), spentAmount, usagePercentage, status)
        );
    }

    private BudgetStatus determineStatus(BigDecimal spentAmount, BigDecimal budgetAmount, Integer warningThresholdPercentage) {
        if (spentAmount.compareTo(budgetAmount) >= 0) {
            return BudgetStatus.EXCEEDED;
        }

        BigDecimal scaledSpending = spentAmount.multiply(ONE_HUNDRED);
        BigDecimal scaledWarningBoundary = budgetAmount.multiply(BigDecimal.valueOf(warningThresholdPercentage));

        if (scaledSpending.compareTo(scaledWarningBoundary) >= 0) {
            return BudgetStatus.WARNING;
        }

        return BudgetStatus.ON_TRACK;
    }
}