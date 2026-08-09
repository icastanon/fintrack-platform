package com.fintrack.workerservice.notification.service;

import com.fintrack.workerservice.budget.model.BudgetEvaluationResult;
import com.fintrack.workerservice.budget.model.BudgetStatus;
import com.fintrack.workerservice.category.entity.Category;
import com.fintrack.workerservice.category.repository.CategoryRepository;
import com.fintrack.workerservice.notification.model.NotificationType;
import com.fintrack.workerservice.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class NotificationService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM uuuu", Locale.US);

    private final NotificationRepository notificationRepository;
    private final CategoryRepository categoryRepository;

    public NotificationService(NotificationRepository notificationRepository, CategoryRepository categoryRepository) {
        this.notificationRepository = notificationRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean createIfRequired(Long userId, Long categoryId, Long transactionId,
                                    LocalDate transactionDate, BudgetEvaluationResult evaluation) {
        if (evaluation.getStatus() == BudgetStatus.ON_TRACK) {
            return false;
        }

        NotificationType notificationType = NotificationType.valueOf(evaluation.getStatus().name());

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalStateException(
                        "Category was not found during notification creation: " + categoryId
                ));

        LocalDate budgetMonth = transactionDate.withDayOfMonth(1);
        String message = buildMessage(
                category.getName(),
                budgetMonth,
                notificationType,
                evaluation.getSpentAmount(),
                evaluation.getBudgetAmount()
        );

        int insertedRows = notificationRepository.insertIfAbsent(
                userId,
                evaluation.getBudgetId(),
                categoryId,
                transactionId,
                budgetMonth,
                notificationType.name(),
                evaluation.getBudgetAmount(),
                evaluation.getSpentAmount(),
                message
        );

        return insertedRows == 1;
    }

    private String buildMessage(String categoryName, LocalDate budgetMonth, NotificationType notificationType,
                                BigDecimal spentAmount, BigDecimal budgetAmount) {
        String month = MONTH_FORMATTER.format(budgetMonth);
        String spent = formatUsd(spentAmount);
        String budget = formatUsd(budgetAmount);

        return switch (notificationType) {
            case WARNING -> "%s spending reached the warning level for %s, with %s spent against a %s budget."
                    .formatted(categoryName, month, spent, budget);
            case EXCEEDED -> "%s spending reached its budget limit for %s, with %s spent against a %s budget."
                    .formatted(categoryName, month, spent, budget);
        };
    }

    private String formatUsd(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}