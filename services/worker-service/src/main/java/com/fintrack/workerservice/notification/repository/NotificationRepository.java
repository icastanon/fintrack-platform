package com.fintrack.workerservice.notification.repository;

import com.fintrack.workerservice.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO notification (
                user_id,
                budget_id,
                category_id,
                transaction_id,
                budget_month,
                notification_type,
                budget_amount,
                spent_amount,
                currency,
                message
            )
            VALUES (
                :userId,
                :budgetId,
                :categoryId,
                :transactionId,
                :budgetMonth,
                :notificationType,
                :budgetAmount,
                :spentAmount,
                :currency,
                :message
            )
            ON CONFLICT ON CONSTRAINT uq_notification_threshold
            DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("budgetId") Long budgetId,
            @Param("categoryId") Long categoryId,
            @Param("transactionId") Long transactionId,
            @Param("budgetMonth") LocalDate budgetMonth,
            @Param("notificationType") String notificationType,
            @Param("budgetAmount") BigDecimal budgetAmount,
            @Param("spentAmount") BigDecimal spentAmount,
            @Param("currency") String currency,
            @Param("message") String message
    );
}