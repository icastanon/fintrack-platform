package com.fintrack.apiservice.notification.service;

import com.fintrack.apiservice.common.dto.PageResponse;
import com.fintrack.apiservice.notification.dto.NotificationFilterRequest;
import com.fintrack.apiservice.notification.dto.NotificationResponse;
import com.fintrack.apiservice.notification.entity.Notification;
import com.fintrack.apiservice.notification.exception.NotificationNotFoundException;
import com.fintrack.apiservice.notification.mapper.NotificationMapper;
import com.fintrack.apiservice.notification.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationRepository notificationRepository, NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
    }

    public PageResponse<NotificationResponse> getNotifications(Long userId, NotificationFilterRequest filter) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Page<Notification> notificationPage = notificationRepository.findAllByUserIdAndUnreadFilter(
                userId,
                filter.isUnreadOnly(),
                pageable
        );

        Page<NotificationResponse> responsePage = notificationPage.map(notificationMapper::toResponse);

        return new PageResponse<>(responsePage);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(NotificationNotFoundException::new);

        notification.markRead(Instant.now());

        return notificationMapper.toResponse(notification);
    }
}