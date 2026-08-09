package com.fintrack.workerservice.notification.service;

import com.fintrack.workerservice.budget.model.BudgetEvaluationResult;
import com.fintrack.workerservice.budget.model.BudgetStatus;
import com.fintrack.workerservice.category.entity.Category;
import com.fintrack.workerservice.category.repository.CategoryRepository;
import com.fintrack.workerservice.notification.model.NotificationType;
import com.fintrack.workerservice.notification.repository.NotificationRepository;
import com.fintrack.workerservice.user.entity.FintrackUser;
import com.fintrack.workerservice.user.repository.FintrackUserRepository;
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
    private final FintrackUserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, CategoryRepository categoryRepository,
                               FintrackUserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean createIfRequired(Long userId, Long categoryId, Long transactionId,
                                    LocalDate transactionDate, BudgetEvaluationResult evaluation) {
        if (evaluation.getStatus() == BudgetStatus.ON_TRACK) {
            return false;
        }

        NotificationType notificationType = NotificationType.valueOf(evaluation.getStatus().name());

        FintrackUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "User was not found during notification creation: " + userId
                ));

        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalStateException(
                        "Category was not found during notification creation: " + categoryId
                ));

        String currency = user.getCurrency();
        LocalDate budgetMonth = transactionDate.withDayOfMonth(1);

        String message = buildMessage(
                category.getName(),
                budgetMonth,
                notificationType,
                evaluation.getSpentAmount(),
                evaluation.getBudgetAmount(),
                currency
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
                currency,
                message
        );

        return insertedRows == 1;
    }

    private String buildMessage(String categoryName, LocalDate budgetMonth, NotificationType notificationType,
                                BigDecimal spentAmount, BigDecimal budgetAmount, String currency) {
        String month = MONTH_FORMATTER.format(budgetMonth);
        String spent = formatMoney(spentAmount, currency);
        String budget = formatMoney(budgetAmount, currency);

        return switch (notificationType) {
            case WARNING -> "%s spending reached the warning level for %s, with %s spent against a %s budget."
                    .formatted(categoryName, month, spent, budget);
            case EXCEEDED -> "%s spending reached its budget limit for %s, with %s spent against a %s budget."
                    .formatted(categoryName, month, spent, budget);
        };
    }

    private String formatMoney(BigDecimal amount, String currency) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + currency;
    }
}