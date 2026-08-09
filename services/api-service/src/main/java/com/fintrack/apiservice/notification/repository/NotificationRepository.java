package com.fintrack.apiservice.notification.repository;

import com.fintrack.apiservice.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = "category")
    Optional<Notification> findByIdAndUserId(Long notificationId, Long userId);

    @EntityGraph(attributePaths = "category")
    @Query("""
            SELECT notification
            FROM Notification notification
            WHERE notification.userId = :userId
              AND (:unreadOnly = false OR notification.readAt IS NULL)
            """)
    Page<Notification> findAllByUserIdAndUnreadFilter(@Param("userId") Long userId,
                                                      @Param("unreadOnly") boolean unreadOnly,
                                                      Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);
}