package com.fintrack.apiservice.notification.controller;

import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.auth.security.JwtService;
import com.fintrack.apiservice.auth.security.RestAccessDeniedHandler;
import com.fintrack.apiservice.auth.security.RestAuthenticationEntryPoint;
import com.fintrack.apiservice.auth.security.SecurityConfig;
import com.fintrack.apiservice.common.dto.PageResponse;
import com.fintrack.apiservice.common.exception.GlobalExceptionHandler;
import com.fintrack.apiservice.notification.dto.NotificationFilterRequest;
import com.fintrack.apiservice.notification.dto.NotificationResponse;
import com.fintrack.apiservice.notification.exception.NotificationNotFoundException;
import com.fintrack.apiservice.notification.model.NotificationType;
import com.fintrack.apiservice.notification.service.NotificationService;
import com.fintrack.apiservice.user.entity.Role;
import com.fintrack.apiservice.user.entity.SupportedCurrency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(7L, "ivan", Role.USER);

        when(jwtService.extractPrincipal("valid-token")).thenReturn(principal);
    }

    @Test
    void getNotificationsReturnsPageAndBindsFilter() throws Exception {
        NotificationResponse notificationResponse = createUnreadResponse();

        PageResponse<NotificationResponse> pageResponse = new PageResponse<>(
                new PageImpl<>(
                        List.of(notificationResponse),
                        PageRequest.of(1, 5),
                        6
                )
        );

        when(notificationService.getNotifications(eq(7L), any(NotificationFilterRequest.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(
                        get("/api/v1/notifications")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("unreadOnly", "true")
                                .queryParam("page", "1")
                                .queryParam("size", "5")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(50))
                .andExpect(jsonPath("$.content[0].budgetId").value(31))
                .andExpect(jsonPath("$.content[0].categoryId").value(2))
                .andExpect(jsonPath("$.content[0].categoryName").value("Restaurants"))
                .andExpect(jsonPath("$.content[0].transactionId").value(100))
                .andExpect(jsonPath("$.content[0].budgetMonth").value("2026-08"))
                .andExpect(jsonPath("$.content[0].notificationType").value("WARNING"))
                .andExpect(jsonPath("$.content[0].budgetAmount").value(600.00))
                .andExpect(jsonPath("$.content[0].spentAmount").value(500.00))
                .andExpect(jsonPath("$.content[0].currency").value("EUR"))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.content[0].readAt").doesNotExist())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));

        ArgumentCaptor<NotificationFilterRequest> filterCaptor =
                ArgumentCaptor.forClass(NotificationFilterRequest.class);

        verify(notificationService).getNotifications(eq(7L), filterCaptor.capture());

        NotificationFilterRequest capturedFilter = filterCaptor.getValue();

        assertThat(capturedFilter.isUnreadOnly()).isTrue();
        assertThat(capturedFilter.getPage()).isEqualTo(1);
        assertThat(capturedFilter.getSize()).isEqualTo(5);
    }

    @Test
    void getNotificationsUsesDefaultFilterValues() throws Exception {
        PageResponse<NotificationResponse> pageResponse = new PageResponse<>(
                new PageImpl<>(
                        List.of(),
                        PageRequest.of(0, 20),
                        0
                )
        );

        when(notificationService.getNotifications(eq(7L), any(NotificationFilterRequest.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(
                        get("/api/v1/notifications")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        ArgumentCaptor<NotificationFilterRequest> filterCaptor =
                ArgumentCaptor.forClass(NotificationFilterRequest.class);

        verify(notificationService).getNotifications(eq(7L), filterCaptor.capture());

        assertThat(filterCaptor.getValue().isUnreadOnly()).isFalse();
        assertThat(filterCaptor.getValue().getPage()).isZero();
        assertThat(filterCaptor.getValue().getSize()).isEqualTo(20);
    }

    @Test
    void getNotificationsWithInvalidPaginationReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/v1/notifications")
                                .header("Authorization", "Bearer valid-token")
                                .queryParam("page", "-1")
                                .queryParam("size", "101")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void getUnreadCountReturnsAuthenticatedUsersCount() throws Exception {
        when(notificationService.getUnreadCount(7L)).thenReturn(4L);

        mockMvc.perform(
                        get("/api/v1/notifications/unread-count")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(4));

        verify(notificationService).getUnreadCount(7L);
    }

    @Test
    void markReadReturnsUpdatedNotification() throws Exception {
        NotificationResponse response = createReadResponse();

        when(notificationService.markRead(7L, 50L)).thenReturn(response);

        mockMvc.perform(
                        patch("/api/v1/notifications/50/read")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").value("2026-08-09T15:30:00Z"));

        verify(notificationService).markRead(7L, 50L);
    }

    @Test
    void markReadForMissingOrUnownedNotificationReturnsNotFound() throws Exception {
        when(notificationService.markRead(7L, 50L)).thenThrow(new NotificationNotFoundException());

        mockMvc.perform(
                        patch("/api/v1/notifications/50/read")
                                .header("Authorization", "Bearer valid-token")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Notification was not found"));

        verify(notificationService).markRead(7L, 50L);
    }

    @Test
    void notificationEndpointsWithoutJwtReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/notifications/50/read"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }

    private NotificationResponse createUnreadResponse() {
        return new NotificationResponse(
                50L,
                31L,
                2L,
                "Restaurants",
                100L,
                YearMonth.of(2026, 8),
                NotificationType.WARNING,
                new BigDecimal("600.00"),
                new BigDecimal("500.00"),
                SupportedCurrency.EUR,
                "Restaurant spending reached the warning threshold.",
                false,
                null,
                Instant.parse("2026-08-09T14:00:00Z")
        );
    }

    private NotificationResponse createReadResponse() {
        return new NotificationResponse(
                50L,
                31L,
                2L,
                "Restaurants",
                100L,
                YearMonth.of(2026, 8),
                NotificationType.WARNING,
                new BigDecimal("600.00"),
                new BigDecimal("500.00"),
                SupportedCurrency.EUR,
                "Restaurant spending reached the warning threshold.",
                true,
                Instant.parse("2026-08-09T15:30:00Z"),
                Instant.parse("2026-08-09T14:00:00Z")
        );
    }
}