package com.fintrack.apiservice.notification.service;

import com.fintrack.apiservice.common.dto.PageResponse;
import com.fintrack.apiservice.notification.dto.NotificationFilterRequest;
import com.fintrack.apiservice.notification.dto.NotificationResponse;
import com.fintrack.apiservice.notification.entity.Notification;
import com.fintrack.apiservice.notification.exception.NotificationNotFoundException;
import com.fintrack.apiservice.notification.mapper.NotificationMapper;
import com.fintrack.apiservice.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void getNotificationsReturnsMappedPageUsingStableSort() {
        Long userId = 10L;

        NotificationFilterRequest filter = new NotificationFilterRequest();
        filter.setUnreadOnly(true);
        filter.setPage(2);
        filter.setSize(10);

        Notification notification = mock(Notification.class);
        NotificationResponse notificationResponse = mock(NotificationResponse.class);

        Page<Notification> notificationPage = new PageImpl<>(
                List.of(notification),
                PageRequest.of(2, 10),
                21
        );

        when(notificationRepository.findAllByUserIdAndUnreadFilter(eq(userId), eq(true), any(Pageable.class)))
                .thenReturn(notificationPage);
        when(notificationMapper.toResponse(notification)).thenReturn(notificationResponse);

        PageResponse<NotificationResponse> result = notificationService.getNotifications(userId, filter);

        assertEquals(List.of(notificationResponse), result.getContent());
        assertEquals(2, result.getPage());
        assertEquals(10, result.getSize());
        assertEquals(21, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertFalse(result.isFirst());
        assertTrue(result.isLast());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(notificationRepository).findAllByUserIdAndUnreadFilter(
                eq(userId),
                eq(true),
                pageableCaptor.capture()
        );

        Pageable pageable = pageableCaptor.getValue();
        Sort.Order createdAtOrder = pageable.getSort().getOrderFor("createdAt");
        Sort.Order idOrder = pageable.getSort().getOrderFor("id");

        assertEquals(2, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertNotNull(createdAtOrder);
        assertNotNull(idOrder);
        assertEquals(Sort.Direction.DESC, createdAtOrder.getDirection());
        assertEquals(Sort.Direction.DESC, idOrder.getDirection());

        verify(notificationMapper).toResponse(notification);
    }

    @Test
    void getUnreadCountReturnsRepositoryCount() {
        Long userId = 10L;

        when(notificationRepository.countByUserIdAndReadAtIsNull(userId)).thenReturn(4L);

        long result = notificationService.getUnreadCount(userId);

        assertEquals(4L, result);
        verify(notificationRepository).countByUserIdAndReadAtIsNull(userId);
    }

    @Test
    void markReadMarksOwnedNotificationAndReturnsResponse() {
        Long userId = 10L;
        Long notificationId = 25L;

        Notification notification = mock(Notification.class);
        NotificationResponse notificationResponse = mock(NotificationResponse.class);

        when(notificationRepository.findByIdAndUserId(notificationId, userId))
                .thenReturn(Optional.of(notification));
        when(notificationMapper.toResponse(notification)).thenReturn(notificationResponse);

        NotificationResponse result = notificationService.markRead(userId, notificationId);

        assertSame(notificationResponse, result);

        ArgumentCaptor<Instant> readAtCaptor = ArgumentCaptor.forClass(Instant.class);

        verify(notification).markRead(readAtCaptor.capture());
        assertNotNull(readAtCaptor.getValue());

        verify(notificationMapper).toResponse(notification);
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markReadThrowsWhenNotificationIsUnavailableToUser() {
        Long userId = 10L;
        Long notificationId = 25L;

        when(notificationRepository.findByIdAndUserId(notificationId, userId))
                .thenReturn(Optional.empty());

        assertThrows(
                NotificationNotFoundException.class,
                () -> notificationService.markRead(userId, notificationId)
        );

        verify(notificationRepository).findByIdAndUserId(notificationId, userId);
        verifyNoInteractions(notificationMapper);
    }
}