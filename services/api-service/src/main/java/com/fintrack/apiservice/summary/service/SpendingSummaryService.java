package com.fintrack.apiservice.summary.service;

import com.fintrack.apiservice.summary.dto.*;
import com.fintrack.apiservice.summary.model.BudgetStatus;
import com.fintrack.apiservice.summary.projection.AccountSpendingProjection;
import com.fintrack.apiservice.summary.projection.BudgetUsageProjection;
import com.fintrack.apiservice.summary.projection.CategorySpendingProjection;
import com.fintrack.apiservice.summary.projection.MonthlyCashFlowProjection;
import com.fintrack.apiservice.summary.repository.SpendingSummaryRepository;
import com.fintrack.apiservice.transaction.entity.ProcessingStatus;
import com.fintrack.apiservice.transaction.entity.TransactionType;
import com.fintrack.apiservice.user.entity.FintrackUser;
import com.fintrack.apiservice.user.exception.FintrackUserNotFoundException;
import com.fintrack.apiservice.user.repository.FintrackUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class SpendingSummaryService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final SpendingSummaryRepository spendingSummaryRepository;
    private final FintrackUserRepository userRepository;

    public SpendingSummaryService(SpendingSummaryRepository spendingSummaryRepository,
                                  FintrackUserRepository userRepository) {
        this.spendingSummaryRepository = spendingSummaryRepository;
        this.userRepository = userRepository;
    }

    public MonthlyCashFlowResponse getMonthlyCashFlow(Long userId, YearMonth month) {
        Objects.requireNonNull(userId, "User ID is required");
        Objects.requireNonNull(month, "Month is required");

        FintrackUser user = userRepository.findById(userId).orElseThrow(() -> new FintrackUserNotFoundException(userId));

        LocalDate startDate = month.atDay(1);
        LocalDate endDateExclusive = month.plusMonths(1).atDay(1);

        MonthlyCashFlowProjection cashFlow = spendingSummaryRepository.summarizeCashFlow(
                userId,
                startDate,
                endDateExclusive,
                TransactionType.INCOME,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        );

        BigDecimal income = normalizeMoney(cashFlow.getIncome());
        BigDecimal expenses = normalizeMoney(cashFlow.getExpenses());
        BigDecimal netCashFlow = income.subtract(expenses);

        return new MonthlyCashFlowResponse(
                month,
                income,
                expenses,
                netCashFlow,
                user.getCurrency()
        );
    }

    public MonthlyCategorySpendingResponse getMonthlySpendingByCategory(Long userId, YearMonth month) {
        Objects.requireNonNull(userId, "User ID is required");
        Objects.requireNonNull(month, "Month is required");

        FintrackUser user = userRepository.findById(userId).orElseThrow(() -> new FintrackUserNotFoundException(userId));

        LocalDate startDate = month.atDay(1);
        LocalDate endDateExclusive = month.plusMonths(1).atDay(1);

        List<CategorySpendingProjection> projections = spendingSummaryRepository.summarizeSpendingByCategory(
                userId,
                startDate,
                endDateExclusive,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        );

        List<CategorySpendingItemResponse> categories = projections.stream()
                .map(projection -> new CategorySpendingItemResponse(
                        projection.getCategoryId(),
                        projection.getCategoryName(),
                        normalizeMoney(projection.getSpentAmount())
                ))
                .toList();

        BigDecimal totalExpenses = categories.stream()
                .map(CategorySpendingItemResponse::getSpentAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        return new MonthlyCategorySpendingResponse(
                month,
                totalExpenses,
                user.getCurrency(),
                categories
        );
    }

    public MonthlyAccountSpendingResponse getMonthlySpendingByAccount(Long userId, YearMonth month) {
        Objects.requireNonNull(userId, "User ID is required");
        Objects.requireNonNull(month, "Month is required");

        FintrackUser user = userRepository.findById(userId).orElseThrow(() -> new FintrackUserNotFoundException(userId));

        LocalDate startDate = month.atDay(1);
        LocalDate endDateExclusive = month.plusMonths(1).atDay(1);

        List<AccountSpendingProjection> projections = spendingSummaryRepository.summarizeSpendingByAccount(
                userId,
                startDate,
                endDateExclusive,
                TransactionType.EXPENSE,
                ProcessingStatus.PROCESSED
        );

        List<AccountSpendingItemResponse> accounts = projections.stream()
                .map(projection -> new AccountSpendingItemResponse(
                        projection.getAccountId(),
                        projection.getAccountName(),
                        normalizeMoney(projection.getSpentAmount())
                ))
                .toList();

        BigDecimal totalExpenses = accounts.stream()
                .map(AccountSpendingItemResponse::getSpentAmount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        return new MonthlyAccountSpendingResponse(
                month,
                totalExpenses,
                user.getCurrency(),
                accounts
        );
    }

    public MonthlyBudgetUsageResponse getMonthlyBudgetUsage(Long userId, YearMonth month) {
        Objects.requireNonNull(userId, "User ID is required");
        Objects.requireNonNull(month, "Month is required");

        FintrackUser user = userRepository.findById(userId).orElseThrow(() -> new FintrackUserNotFoundException(userId));

        LocalDate monthStart = month.atDay(1);
        LocalDate endDateExclusive = month.plusMonths(1).atDay(1);

        List<BudgetUsageProjection> projections = spendingSummaryRepository.summarizeBudgetUsage(
                userId,
                monthStart,
                endDateExclusive,
                TransactionType.EXPENSE.name(),
                ProcessingStatus.PROCESSED.name()
        );

        List<BudgetUsageItemResponse> budgets = projections.stream()
                .map(projection -> {
                    BigDecimal budgetAmount = normalizeMoney(projection.getBudgetAmount());
                    BigDecimal spentAmount = normalizeMoney(projection.getSpentAmount());
                    BigDecimal remainingAmount = budgetAmount.subtract(spentAmount);

                    BigDecimal usagePercentage = spentAmount.multiply(ONE_HUNDRED)
                            .divide(budgetAmount, 2, RoundingMode.HALF_UP);

                    BudgetStatus status;

                    if (spentAmount.compareTo(budgetAmount) >= 0) {
                        status = BudgetStatus.EXCEEDED;
                    } else {
                        BigDecimal scaledSpending = spentAmount.multiply(ONE_HUNDRED);
                        BigDecimal scaledWarningBoundary = budgetAmount.multiply(
                                BigDecimal.valueOf(projection.getWarningThresholdPercentage())
                        );

                        status = scaledSpending.compareTo(scaledWarningBoundary) >= 0
                                ? BudgetStatus.WARNING
                                : BudgetStatus.ON_TRACK;
                    }

                    return new BudgetUsageItemResponse(
                            projection.getBudgetId(),
                            projection.getCategoryId(),
                            projection.getCategoryName(),
                            budgetAmount,
                            projection.getWarningThresholdPercentage(),
                            spentAmount,
                            remainingAmount,
                            usagePercentage,
                            status
                    );
                })
                .toList();

        return new MonthlyBudgetUsageResponse(
                month,
                user.getCurrency(),
                budgets
        );
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(2) : amount.setScale(2);
    }
}