package com.fintrack.apiservice.summary.service;

import com.fintrack.apiservice.summary.dto.MonthlyAccountSpendingResponse;
import com.fintrack.apiservice.summary.dto.MonthlyBudgetUsageResponse;
import com.fintrack.apiservice.summary.dto.MonthlyCashFlowResponse;
import com.fintrack.apiservice.summary.dto.MonthlyCategorySpendingResponse;
import com.fintrack.apiservice.summary.model.BudgetStatus;
import com.fintrack.apiservice.summary.projection.AccountSpendingProjection;
import com.fintrack.apiservice.summary.projection.BudgetUsageProjection;
import com.fintrack.apiservice.summary.projection.CategorySpendingProjection;
import com.fintrack.apiservice.summary.projection.MonthlyCashFlowProjection;
import com.fintrack.apiservice.summary.repository.SpendingSummaryRepository;
import com.fintrack.apiservice.transaction.entity.ProcessingStatus;
import com.fintrack.apiservice.transaction.entity.TransactionType;
import com.fintrack.apiservice.user.entity.FintrackUser;
import com.fintrack.apiservice.user.entity.SupportedCurrency;
import com.fintrack.apiservice.user.exception.FintrackUserNotFoundException;
import com.fintrack.apiservice.user.repository.FintrackUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpendingSummaryServiceTest {

    private static final Long USER_ID = 42L;
    private static final YearMonth MONTH = YearMonth.of(2026, 8);
    private static final LocalDate MONTH_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate NEXT_MONTH_START = LocalDate.of(2026, 9, 1);

    @Mock
    private SpendingSummaryRepository spendingSummaryRepository;

    @Mock
    private FintrackUserRepository userRepository;

    @InjectMocks
    private SpendingSummaryService spendingSummaryService;

    @Test
    void getMonthlyCashFlowReturnsAggregatedMonthlySummary() {
        FintrackUser user = mock(FintrackUser.class);
        MonthlyCashFlowProjection cashFlow = mock(MonthlyCashFlowProjection.class);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.getCurrency()).thenReturn(SupportedCurrency.EUR);

        when(spendingSummaryRepository.summarizeCashFlow(
                USER_ID,
                MONTH_START,
                NEXT_MONTH_START,
                TransactionType.INCOME,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        )).thenReturn(cashFlow);

        when(cashFlow.getIncome()).thenReturn(new BigDecimal("1000.00"));
        when(cashFlow.getExpenses()).thenReturn(new BigDecimal("250.50"));

        MonthlyCashFlowResponse result = spendingSummaryService.getMonthlyCashFlow(USER_ID, MONTH);

        assertThat(result.getMonth()).isEqualTo(MONTH);
        assertThat(result.getIncome()).isEqualByComparingTo("1000.00");
        assertThat(result.getExpenses()).isEqualByComparingTo("250.50");
        assertThat(result.getNetCashFlow()).isEqualByComparingTo("749.50");
        assertThat(result.getCurrency()).isEqualTo(SupportedCurrency.EUR);
    }

    @Test
    void getMonthlyCashFlowNormalizesMissingTotalsToZero() {
        FintrackUser user = mock(FintrackUser.class);
        MonthlyCashFlowProjection cashFlow = mock(MonthlyCashFlowProjection.class);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.getCurrency()).thenReturn(SupportedCurrency.USD);

        when(spendingSummaryRepository.summarizeCashFlow(
                USER_ID,
                MONTH_START,
                NEXT_MONTH_START,
                TransactionType.INCOME,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        )).thenReturn(cashFlow);

        MonthlyCashFlowResponse result = spendingSummaryService.getMonthlyCashFlow(USER_ID, MONTH);

        assertThat(result.getIncome()).isEqualByComparingTo("0.00");
        assertThat(result.getExpenses()).isEqualByComparingTo("0.00");
        assertThat(result.getNetCashFlow()).isEqualByComparingTo("0.00");
        assertThat(result.getCurrency()).isEqualTo(SupportedCurrency.USD);
    }

    @Test
    void getMonthlyCashFlowThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spendingSummaryService.getMonthlyCashFlow(USER_ID, MONTH))
                .isInstanceOf(FintrackUserNotFoundException.class)
                .hasMessage("User not found with id: " + USER_ID);

        verifyNoInteractions(spendingSummaryRepository);
    }

    @Test
    void getMonthlyCashFlowThrowsWhenUserIdIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> spendingSummaryService.getMonthlyCashFlow(null, MONTH))
                .withMessage("User ID is required");

        verifyNoInteractions(userRepository, spendingSummaryRepository);
    }

    @Test
    void getMonthlyCashFlowThrowsWhenMonthIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> spendingSummaryService.getMonthlyCashFlow(USER_ID, null))
                .withMessage("Month is required");

        verifyNoInteractions(userRepository, spendingSummaryRepository);
    }

    @Test
    void getMonthlySpendingByCategoryReturnsMappedCategoriesAndTotal() {
        FintrackUser user = mock(FintrackUser.class);
        CategorySpendingProjection groceries = mock(CategorySpendingProjection.class);
        CategorySpendingProjection restaurants = mock(CategorySpendingProjection.class);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.getCurrency()).thenReturn(SupportedCurrency.GBP);

        when(groceries.getCategoryId()).thenReturn(10L);
        when(groceries.getCategoryName()).thenReturn("Groceries");
        when(groceries.getSpentAmount()).thenReturn(new BigDecimal("250"));

        when(restaurants.getCategoryId()).thenReturn(11L);
        when(restaurants.getCategoryName()).thenReturn("Restaurants");
        when(restaurants.getSpentAmount()).thenReturn(new BigDecimal("50.50"));

        when(spendingSummaryRepository.summarizeSpendingByCategory(
                USER_ID,
                MONTH_START,
                NEXT_MONTH_START,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        )).thenReturn(List.of(groceries, restaurants));

        MonthlyCategorySpendingResponse result =
                spendingSummaryService.getMonthlySpendingByCategory(USER_ID, MONTH);

        assertThat(result.getMonth()).isEqualTo(MONTH);
        assertThat(result.getTotalExpenses()).isEqualByComparingTo("300.50");
        assertThat(result.getCurrency()).isEqualTo(SupportedCurrency.GBP);
        assertThat(result.getCategories()).hasSize(2);

        assertThat(result.getCategories().get(0).getCategoryId()).isEqualTo(10L);
        assertThat(result.getCategories().get(0).getCategoryName()).isEqualTo("Groceries");
        assertThat(result.getCategories().get(0).getSpentAmount()).isEqualByComparingTo("250.00");

        assertThat(result.getCategories().get(1).getCategoryId()).isEqualTo(11L);
        assertThat(result.getCategories().get(1).getCategoryName()).isEqualTo("Restaurants");
        assertThat(result.getCategories().get(1).getSpentAmount()).isEqualByComparingTo("50.50");
    }

    @Test
    void getMonthlySpendingByCategoryReturnsEmptyReportWhenUserHasNoExpenses() {
        FintrackUser user = mock(FintrackUser.class);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.getCurrency()).thenReturn(SupportedCurrency.CAD);

        when(spendingSummaryRepository.summarizeSpendingByCategory(
                USER_ID,
                MONTH_START,
                NEXT_MONTH_START,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        )).thenReturn(List.of());

        MonthlyCategorySpendingResponse result =
                spendingSummaryService.getMonthlySpendingByCategory(USER_ID, MONTH);

        assertThat(result.getMonth()).isEqualTo(MONTH);
        assertThat(result.getTotalExpenses()).isEqualByComparingTo("0.00");
        assertThat(result.getCurrency()).isEqualTo(SupportedCurrency.CAD);
        assertThat(result.getCategories()).isEmpty();
    }

    @Test
    void getMonthlySpendingByCategoryThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spendingSummaryService.getMonthlySpendingByCategory(USER_ID, MONTH))
                .isInstanceOf(FintrackUserNotFoundException.class)
                .hasMessage("User not found with id: " + USER_ID);

        verifyNoInteractions(spendingSummaryRepository);
    }

    @Test
    void getMonthlySpendingByCategoryThrowsWhenUserIdIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> spendingSummaryService.getMonthlySpendingByCategory(null, MONTH))
                .withMessage("User ID is required");

        verifyNoInteractions(userRepository, spendingSummaryRepository);
    }

    @Test
    void getMonthlySpendingByCategoryThrowsWhenMonthIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> spendingSummaryService.getMonthlySpendingByCategory(USER_ID, null))
                .withMessage("Month is required");

        verifyNoInteractions(userRepository, spendingSummaryRepository);
    }

    @Test
    void getMonthlySpendingByAccountReturnsMappedAccountsAndTotal() {
        FintrackUser user = mock(FintrackUser.class);
        AccountSpendingProjection primaryChecking = mock(AccountSpendingProjection.class);
        AccountSpendingProjection travelCard = mock(AccountSpendingProjection.class);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.getCurrency()).thenReturn(SupportedCurrency.AUD);

        when(primaryChecking.getAccountId()).thenReturn(21L);
        when(primaryChecking.getAccountName()).thenReturn("Primary checking");
        when(primaryChecking.getSpentAmount()).thenReturn(new BigDecimal("250"));

        when(travelCard.getAccountId()).thenReturn(23L);
        when(travelCard.getAccountName()).thenReturn("Travel card");
        when(travelCard.getSpentAmount()).thenReturn(new BigDecimal("50.50"));

        when(spendingSummaryRepository.summarizeSpendingByAccount(
                USER_ID,
                MONTH_START,
                NEXT_MONTH_START,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        )).thenReturn(List.of(primaryChecking, travelCard));

        MonthlyAccountSpendingResponse result =
                spendingSummaryService.getMonthlySpendingByAccount(USER_ID, MONTH);

        assertThat(result.getMonth()).isEqualTo(MONTH);
        assertThat(result.getTotalExpenses()).isEqualByComparingTo("300.50");
        assertThat(result.getCurrency()).isEqualTo(SupportedCurrency.AUD);
        assertThat(result.getAccounts()).hasSize(2);

        assertThat(result.getAccounts().get(0).getAccountId()).isEqualTo(21L);
        assertThat(result.getAccounts().get(0).getAccountName()).isEqualTo("Primary checking");
        assertThat(result.getAccounts().get(0).getSpentAmount()).isEqualByComparingTo("250.00");

        assertThat(result.getAccounts().get(1).getAccountId()).isEqualTo(23L);
        assertThat(result.getAccounts().get(1).getAccountName()).isEqualTo("Travel card");
        assertThat(result.getAccounts().get(1).getSpentAmount()).isEqualByComparingTo("50.50");
    }

    @Test
    void getMonthlySpendingByAccountReturnsEmptyReportWhenUserHasNoExpenses() {
        FintrackUser user = mock(FintrackUser.class);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.getCurrency()).thenReturn(SupportedCurrency.USD);

        when(spendingSummaryRepository.summarizeSpendingByAccount(
                USER_ID,
                MONTH_START,
                NEXT_MONTH_START,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        )).thenReturn(List.of());

        MonthlyAccountSpendingResponse result =
                spendingSummaryService.getMonthlySpendingByAccount(USER_ID, MONTH);

        assertThat(result.getMonth()).isEqualTo(MONTH);
        assertThat(result.getTotalExpenses()).isEqualByComparingTo("0.00");
        assertThat(result.getCurrency()).isEqualTo(SupportedCurrency.USD);
        assertThat(result.getAccounts()).isEmpty();
    }

    @Test
    void getMonthlySpendingByAccountThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spendingSummaryService.getMonthlySpendingByAccount(USER_ID, MONTH))
                .isInstanceOf(FintrackUserNotFoundException.class)
                .hasMessage("User not found with id: " + USER_ID);

        verifyNoInteractions(spendingSummaryRepository);
    }

    @Test
    void getMonthlySpendingByAccountThrowsWhenUserIdIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> spendingSummaryService.getMonthlySpendingByAccount(null, MONTH))
                .withMessage("User ID is required");

        verifyNoInteractions(userRepository, spendingSummaryRepository);
    }

    @Test
    void getMonthlySpendingByAccountThrowsWhenMonthIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> spendingSummaryService.getMonthlySpendingByAccount(USER_ID, null))
                .withMessage("Month is required");

        verifyNoInteractions(userRepository, spendingSummaryRepository);
    }

    @Test
    void getMonthlyBudgetUsageReturnsAllStatusesAndRemainingAmounts() {
        FintrackUser user = mock(FintrackUser.class);

        BudgetUsageProjection groceries = createBudgetUsageProjection(
                41L,
                31L,
                "Groceries",
                "300.00",
                80,
                "250.00"
        );

        BudgetUsageProjection restaurants = createBudgetUsageProjection(
                42L,
                32L,
                "Restaurants",
                "50.00",
                80,
                "50.00"
        );

        BudgetUsageProjection utilities = createBudgetUsageProjection(
                43L,
                33L,
                "Utilities",
                "100.00",
                80,
                "0.00"
        );

        BudgetUsageProjection travel = createBudgetUsageProjection(
                44L,
                34L,
                "Travel",
                "100.00",
                80,
                "125.00"
        );

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.getCurrency()).thenReturn(SupportedCurrency.EUR);

        when(spendingSummaryRepository.summarizeBudgetUsage(
                USER_ID,
                MONTH_START,
                NEXT_MONTH_START,
                TransactionType.EXPENSE.name(),
                ProcessingStatus.PROCESSED.name()
        )).thenReturn(List.of(groceries, restaurants, utilities, travel));

        MonthlyBudgetUsageResponse result = spendingSummaryService.getMonthlyBudgetUsage(USER_ID, MONTH);

        assertThat(result.getMonth()).isEqualTo(MONTH);
        assertThat(result.getCurrency()).isEqualTo(SupportedCurrency.EUR);
        assertThat(result.getBudgets()).hasSize(4);

        assertThat(result.getBudgets().get(0).getBudgetId()).isEqualTo(41L);
        assertThat(result.getBudgets().get(0).getCategoryName()).isEqualTo("Groceries");
        assertThat(result.getBudgets().get(0).getBudgetAmount()).isEqualByComparingTo("300.00");
        assertThat(result.getBudgets().get(0).getSpentAmount()).isEqualByComparingTo("250.00");
        assertThat(result.getBudgets().get(0).getRemainingAmount()).isEqualByComparingTo("50.00");
        assertThat(result.getBudgets().get(0).getUsagePercentage()).isEqualByComparingTo("83.33");
        assertThat(result.getBudgets().get(0).getStatus()).isEqualTo(BudgetStatus.WARNING);

        assertThat(result.getBudgets().get(1).getBudgetId()).isEqualTo(42L);
        assertThat(result.getBudgets().get(1).getCategoryName()).isEqualTo("Restaurants");
        assertThat(result.getBudgets().get(1).getRemainingAmount()).isEqualByComparingTo("0.00");
        assertThat(result.getBudgets().get(1).getUsagePercentage()).isEqualByComparingTo("100.00");
        assertThat(result.getBudgets().get(1).getStatus()).isEqualTo(BudgetStatus.EXCEEDED);

        assertThat(result.getBudgets().get(2).getBudgetId()).isEqualTo(43L);
        assertThat(result.getBudgets().get(2).getCategoryName()).isEqualTo("Utilities");
        assertThat(result.getBudgets().get(2).getSpentAmount()).isEqualByComparingTo("0.00");
        assertThat(result.getBudgets().get(2).getRemainingAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getBudgets().get(2).getUsagePercentage()).isEqualByComparingTo("0.00");
        assertThat(result.getBudgets().get(2).getStatus()).isEqualTo(BudgetStatus.ON_TRACK);

        assertThat(result.getBudgets().get(3).getBudgetId()).isEqualTo(44L);
        assertThat(result.getBudgets().get(3).getCategoryName()).isEqualTo("Travel");
        assertThat(result.getBudgets().get(3).getSpentAmount()).isEqualByComparingTo("125.00");
        assertThat(result.getBudgets().get(3).getRemainingAmount()).isEqualByComparingTo("-25.00");
        assertThat(result.getBudgets().get(3).getUsagePercentage()).isEqualByComparingTo("125.00");
        assertThat(result.getBudgets().get(3).getStatus()).isEqualTo(BudgetStatus.EXCEEDED);
    }

    @Test
    void getMonthlyBudgetUsageReturnsEmptyReportWhenUserHasNoBudgets() {
        FintrackUser user = mock(FintrackUser.class);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.getCurrency()).thenReturn(SupportedCurrency.GBP);

        when(spendingSummaryRepository.summarizeBudgetUsage(
                USER_ID,
                MONTH_START,
                NEXT_MONTH_START,
                TransactionType.EXPENSE.name(),
                ProcessingStatus.PROCESSED.name()
        )).thenReturn(List.of());

        MonthlyBudgetUsageResponse result = spendingSummaryService.getMonthlyBudgetUsage(USER_ID, MONTH);

        assertThat(result.getMonth()).isEqualTo(MONTH);
        assertThat(result.getCurrency()).isEqualTo(SupportedCurrency.GBP);
        assertThat(result.getBudgets()).isEmpty();
    }

    @Test
    void getMonthlyBudgetUsageThrowsWhenUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spendingSummaryService.getMonthlyBudgetUsage(USER_ID, MONTH))
                .isInstanceOf(FintrackUserNotFoundException.class)
                .hasMessage("User not found with id: " + USER_ID);

        verifyNoInteractions(spendingSummaryRepository);
    }

    @Test
    void getMonthlyBudgetUsageThrowsWhenUserIdIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> spendingSummaryService.getMonthlyBudgetUsage(null, MONTH))
                .withMessage("User ID is required");

        verifyNoInteractions(userRepository, spendingSummaryRepository);
    }

    @Test
    void getMonthlyBudgetUsageThrowsWhenMonthIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> spendingSummaryService.getMonthlyBudgetUsage(USER_ID, null))
                .withMessage("Month is required");

        verifyNoInteractions(userRepository, spendingSummaryRepository);
    }

    private BudgetUsageProjection createBudgetUsageProjection(Long budgetId,
                                                              Long categoryId,
                                                              String categoryName,
                                                              String budgetAmount,
                                                              Integer warningThresholdPercentage,
                                                              String spentAmount) {
        BudgetUsageProjection projection = mock(BudgetUsageProjection.class);

        when(projection.getBudgetId()).thenReturn(budgetId);
        when(projection.getCategoryId()).thenReturn(categoryId);
        when(projection.getCategoryName()).thenReturn(categoryName);
        when(projection.getBudgetAmount()).thenReturn(new BigDecimal(budgetAmount));
        when(projection.getWarningThresholdPercentage()).thenReturn(warningThresholdPercentage);
        when(projection.getSpentAmount()).thenReturn(new BigDecimal(spentAmount));

        return projection;
    }
}