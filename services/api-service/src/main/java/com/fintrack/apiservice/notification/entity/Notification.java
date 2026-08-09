package com.fintrack.apiservice.notification.entity;

import com.fintrack.apiservice.category.entity.Category;
import com.fintrack.apiservice.notification.model.NotificationType;
import com.fintrack.apiservice.user.entity.SupportedCurrency;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "notification")
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, updatable = false)
    private Category category;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private SupportedCurrency currency;

    @Column(name = "message", nullable = false, updatable = false, length = 500)
    private String message;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public void markRead(Instant readAt) {
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }
}