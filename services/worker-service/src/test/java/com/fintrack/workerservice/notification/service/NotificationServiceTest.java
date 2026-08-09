package com.fintrack.workerservice.notification.service;

import com.fintrack.workerservice.budget.model.BudgetEvaluationResult;
import com.fintrack.workerservice.budget.model.BudgetStatus;
import com.fintrack.workerservice.category.entity.Category;
import com.fintrack.workerservice.category.repository.CategoryRepository;
import com.fintrack.workerservice.notification.repository.NotificationRepository;
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
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private Category category;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void createIfRequired_whenBudgetIsOnTrack_doesNotCreateNotification() {
        BudgetEvaluationResult evaluation = createEvaluation(
                new BigDecimal("40.00"),
                new BigDecimal("40.00"),
                BudgetStatus.ON_TRACK
        );

        boolean created = notificationService.createIfRequired(
                25L,
                4L,
                88L,
                LocalDate.of(2026, 8, 8),
                evaluation
        );

        assertThat(created).isFalse();

        verifyNoInteractions(categoryRepository, notificationRepository);
    }

    @Test
    void createIfRequired_whenBudgetIsWarning_createsHistoricalWarning() {
        BudgetEvaluationResult evaluation = createEvaluation(
                new BigDecimal("80.00"),
                new BigDecimal("80.00"),
                BudgetStatus.WARNING
        );

        when(categoryRepository.findById(4L)).thenReturn(Optional.of(category));
        when(category.getName()).thenReturn("Groceries");
        when(notificationRepository.insertIfAbsent(
                25L,
                10L,
                4L,
                88L,
                LocalDate.of(2026, 8, 1),
                "WARNING",
                new BigDecimal("100.00"),
                new BigDecimal("80.00"),
                "Groceries spending reached the warning level for August 2026, with $80.00 spent against a $100.00 budget."
        )).thenReturn(1);

        boolean created = notificationService.createIfRequired(
                25L,
                4L,
                88L,
                LocalDate.of(2026, 8, 8),
                evaluation
        );

        assertThat(created).isTrue();

        verify(categoryRepository).findById(4L);
        verify(notificationRepository).insertIfAbsent(
                25L,
                10L,
                4L,
                88L,
                LocalDate.of(2026, 8, 1),
                "WARNING",
                new BigDecimal("100.00"),
                new BigDecimal("80.00"),
                "Groceries spending reached the warning level for August 2026, with $80.00 spent against a $100.00 budget."
        );
    }

    @Test
    void createIfRequired_whenBudgetIsExceeded_createsHistoricalExceededNotification() {
        BudgetEvaluationResult evaluation = createEvaluation(
                new BigDecimal("120.00"),
                new BigDecimal("120.00"),
                BudgetStatus.EXCEEDED
        );

        when(categoryRepository.findById(4L)).thenReturn(Optional.of(category));
        when(category.getName()).thenReturn("Groceries");
        when(notificationRepository.insertIfAbsent(
                25L,
                10L,
                4L,
                88L,
                LocalDate.of(2026, 8, 1),
                "EXCEEDED",
                new BigDecimal("100.00"),
                new BigDecimal("120.00"),
                "Groceries spending reached its budget limit for August 2026, with $120.00 spent against a $100.00 budget."
        )).thenReturn(1);

        boolean created = notificationService.createIfRequired(
                25L,
                4L,
                88L,
                LocalDate.of(2026, 8, 8),
                evaluation
        );

        assertThat(created).isTrue();

        verify(notificationRepository).insertIfAbsent(
                25L,
                10L,
                4L,
                88L,
                LocalDate.of(2026, 8, 1),
                "EXCEEDED",
                new BigDecimal("100.00"),
                new BigDecimal("120.00"),
                "Groceries spending reached its budget limit for August 2026, with $120.00 spent against a $100.00 budget."
        );
    }

    @Test
    void createIfRequired_whenThresholdNotificationAlreadyExists_returnsFalse() {
        BudgetEvaluationResult evaluation = createEvaluation(
                new BigDecimal("90.00"),
                new BigDecimal("90.00"),
                BudgetStatus.WARNING
        );

        when(categoryRepository.findById(4L)).thenReturn(Optional.of(category));
        when(category.getName()).thenReturn("Groceries");
        when(notificationRepository.insertIfAbsent(
                25L,
                10L,
                4L,
                88L,
                LocalDate.of(2026, 8, 1),
                "WARNING",
                new BigDecimal("100.00"),
                new BigDecimal("90.00"),
                "Groceries spending reached the warning level for August 2026, with $90.00 spent against a $100.00 budget."
        )).thenReturn(0);

        boolean created = notificationService.createIfRequired(
                25L,
                4L,
                88L,
                LocalDate.of(2026, 8, 8),
                evaluation
        );

        assertThat(created).isFalse();

        verify(notificationRepository).insertIfAbsent(
                25L,
                10L,
                4L,
                88L,
                LocalDate.of(2026, 8, 1),
                "WARNING",
                new BigDecimal("100.00"),
                new BigDecimal("90.00"),
                "Groceries spending reached the warning level for August 2026, with $90.00 spent against a $100.00 budget."
        );
    }

    private BudgetEvaluationResult createEvaluation(BigDecimal spentAmount, BigDecimal usagePercentage,
                                                    BudgetStatus status) {
        return new BudgetEvaluationResult(
                10L,
                new BigDecimal("100.00"),
                spentAmount,
                usagePercentage,
                status
        );
    }
}