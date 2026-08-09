package com.fintrack.apiservice.notification.controller;

import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.common.dto.PageResponse;
import com.fintrack.apiservice.notification.dto.NotificationFilterRequest;
import com.fintrack.apiservice.notification.dto.NotificationResponse;
import com.fintrack.apiservice.notification.dto.NotificationUnreadCountResponse;
import com.fintrack.apiservice.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.fintrack.apiservice.common.config.OpenApiConfig.BEARER_AUTH;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "View and manage the authenticated user's historical notifications")
@SecurityRequirement(name = BEARER_AUTH)
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(
            summary = "List notifications",
            description = "Returns the authenticated user's notifications with optional unread filtering and newest-first pagination"
    )
    public ResponseEntity<PageResponse<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @Valid @ModelAttribute NotificationFilterRequest filter
    ) {
        PageResponse<NotificationResponse> response = notificationService.getNotifications(principal.getUserId(), filter);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "Get unread notification count",
            description = "Returns the number of unread notifications belonging to the authenticated user"
    )
    public ResponseEntity<NotificationUnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal
    ) {
        long unreadCount = notificationService.getUnreadCount(principal.getUserId());

        return ResponseEntity.ok(new NotificationUnreadCountResponse(unreadCount));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(
            summary = "Mark notification as read",
            description = "Marks one owned notification as read and returns its updated state"
    )
    public ResponseEntity<NotificationResponse> markRead(
            @AuthenticationPrincipal AuthenticatedUserPrincipal principal,
            @PathVariable Long notificationId
    ) {
        NotificationResponse response = notificationService.markRead(principal.getUserId(), notificationId);

        return ResponseEntity.ok(response);
    }
}