package com.fintrack.apiservice.notification.dto;

import com.fintrack.apiservice.notification.model.NotificationType;
import com.fintrack.apiservice.user.entity.SupportedCurrency;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

@Getter
@AllArgsConstructor
public class NotificationResponse {

    private final Long id;
    private final Long budgetId;
    private final Long categoryId;
    private final String categoryName;
    private final Long transactionId;
    private final YearMonth budgetMonth;
    private final NotificationType notificationType;
    private final BigDecimal budgetAmount;
    private final BigDecimal spentAmount;
    private final SupportedCurrency currency;
    private final String message;
    private final boolean read;
    private final Instant readAt;
    private final Instant createdAt;
}