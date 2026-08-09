package com.fintrack.workerservice.notification.entity;

import com.fintrack.workerservice.notification.model.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "notification", uniqueConstraints = @UniqueConstraint(name = "uq_notification_threshold", columnNames = {"user_id", "category_id", "budget_month", "notification_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "budget_id", updatable = false)
    private Long budgetId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private Long categoryId;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private Long transactionId;

    @Column(name = "budget_month", nullable = false, updatable = false)
    private LocalDate budgetMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, updatable = false, length = 20)
    private NotificationType notificationType;

    @Column(name = "budget_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal budgetAmount;

    @Column(name = "spent_amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal spentAmount;

    @Column(name = "message", nullable = false, updatable = false, length = 500)
    private String message;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}