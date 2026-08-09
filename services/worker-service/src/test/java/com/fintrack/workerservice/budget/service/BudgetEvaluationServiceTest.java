package com.fintrack.workerservice.budget.service;

import com.fintrack.workerservice.budget.entity.Budget;
import com.fintrack.workerservice.budget.model.BudgetEvaluationResult;
import com.fintrack.workerservice.budget.model.BudgetStatus;
import com.fintrack.workerservice.budget.repository.BudgetRepository;
import com.fintrack.workerservice.transaction.repository.FinancialTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetEvaluationServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private FinancialTransactionRepository financialTransactionRepository;

    @Mock
    private Budget budget;

    @InjectMocks
    private BudgetEvaluationService budgetEvaluationService;

    @Test
    void evaluate_whenBudgetDoesNotExist_returnsEmpty() {
        when(budgetRepository.findForEvaluation(25L, 4L, LocalDate.of(2026, 8, 1)))
                .thenReturn(Optional.empty());

        Optional<BudgetEvaluationResult> result = budgetEvaluationService.evaluate(
                25L,
                4L,
                LocalDate.of(2026, 8, 8)
        );

        assertThat(result).isEmpty();

        verifyNoInteractions(financialTransactionRepository);
    }

    @Test
    void evaluate_whenSpendingIsBelowThreshold_returnsOnTrack() {
        prepareBudget(new BigDecimal("100.00"), 80);
        when(financialTransactionRepository.sumProcessedExpenses(
                25L,
                4L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 1)
        )).thenReturn(new BigDecimal("40.00"));

        BudgetEvaluationResult result = budgetEvaluationService
                .evaluate(25L, 4L, LocalDate.of(2026, 8, 8))
                .orElseThrow();

        assertThat(result.getSpentAmount()).isEqualByComparingTo("40.00");
        assertThat(result.getUsagePercentage()).isEqualByComparingTo("40.00");
        assertThat(result.getStatus()).isEqualTo(BudgetStatus.ON_TRACK);

        verify(financialTransactionRepository).flush();
    }

    @Test
    void evaluate_whenSpendingReachesWarningThreshold_returnsWarning() {
        prepareBudget(new BigDecimal("100.00"), 80);
        when(financialTransactionRepository.sumProcessedExpenses(
                25L,
                4L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 1)
        )).thenReturn(new BigDecimal("80.00"));

        BudgetEvaluationResult result = budgetEvaluationService
                .evaluate(25L, 4L, LocalDate.of(2026, 8, 8))
                .orElseThrow();

        assertThat(result.getUsagePercentage()).isEqualByComparingTo("80.00");
        assertThat(result.getStatus()).isEqualTo(BudgetStatus.WARNING);
    }

    @Test
    void evaluate_whenSpendingReachesBudgetAmount_returnsExceeded() {
        prepareBudget(new BigDecimal("100.00"), 80);
        when(financialTransactionRepository.sumProcessedExpenses(
                25L,
                4L,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 1)
        )).thenReturn(new BigDecimal("100.00"));

        BudgetEvaluationResult result = budgetEvaluationService
                .evaluate(25L, 4L, LocalDate.of(2026, 8, 8))
                .orElseThrow();

        assertThat(result.getUsagePercentage()).isEqualByComparingTo("100.00");
        assertThat(result.getStatus()).isEqualTo(BudgetStatus.EXCEEDED);
    }

    private void prepareBudget(BigDecimal amount, Integer warningThresholdPercentage) {
        when(budgetRepository.findForEvaluation(25L, 4L, LocalDate.of(2026, 8, 1)))
                .thenReturn(Optional.of(budget));
        when(budget.getId()).thenReturn(10L);
        when(budget.getAmount()).thenReturn(amount);
        when(budget.getWarningThresholdPercentage()).thenReturn(warningThresholdPercentage);
    }
}