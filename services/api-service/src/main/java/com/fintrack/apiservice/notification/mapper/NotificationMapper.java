package com.fintrack.apiservice.notification.mapper;

import com.fintrack.apiservice.notification.dto.NotificationResponse;
import com.fintrack.apiservice.notification.entity.Notification;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getBudgetId(),
                notification.getCategory().getId(),
                notification.getCategory().getName(),
                notification.getTransactionId(),
                YearMonth.from(notification.getBudgetMonth()),
                notification.getNotificationType(),
                notification.getBudgetAmount(),
                notification.getSpentAmount(),
                notification.getCurrency(),
                notification.getMessage(),
                notification.getReadAt() != null,
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}