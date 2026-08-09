package com.fintrack.apiservice.auth.controller;

import com.fintrack.apiservice.auth.dto.AuthResponse;
import com.fintrack.apiservice.auth.dto.LoginRequest;
import com.fintrack.apiservice.auth.dto.RegisterRequest;
import com.fintrack.apiservice.auth.refresh.dto.RefreshTokenRequest;
import com.fintrack.apiservice.auth.refresh.exception.InvalidRefreshTokenException;
import com.fintrack.apiservice.auth.security.JwtService;
import com.fintrack.apiservice.auth.security.RestAccessDeniedHandler;
import com.fintrack.apiservice.auth.security.RestAuthenticationEntryPoint;
import com.fintrack.apiservice.auth.security.SecurityConfig;
import com.fintrack.apiservice.auth.service.AuthService;
import com.fintrack.apiservice.common.exception.GlobalExceptionHandler;
import com.fintrack.apiservice.user.entity.SupportedCurrency;
import com.fintrack.apiservice.user.exception.EmailAlreadyExistsException;
import com.fintrack.apiservice.user.exception.UsernameAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    /*
     * Required because the real SecurityConfig constructs
     * JwtAuthenticationFilter using JwtService.
     */
    @MockitoBean
    private JwtService jwtService;

    @Test
    void registerReturnsCreated() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "ivan",
                                          "email": "ivan@example.com",
                                          "password": "plain-password",
                                          "currency": "USD"
                                        }
                                        """)
                )
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterRequest> requestCaptor = ArgumentCaptor.forClass(RegisterRequest.class);

        verify(authService).register(requestCaptor.capture());

        RegisterRequest capturedRequest = requestCaptor.getValue();

        assertThat(capturedRequest.getUsername()).isEqualTo("ivan");

        assertThat(capturedRequest.getEmail()).isEqualTo("ivan@example.com");

        assertThat(capturedRequest.getPassword()).isEqualTo("plain-password");

        assertThat(capturedRequest.getCurrency()).isEqualTo(SupportedCurrency.USD);
    }

    @Test
    void registerWithInvalidRequestReturnsBadRequest() throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": " ",
                                          "email": "not-an-email",
                                          "password": " "
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void registerWithDuplicateUsernameReturnsConflict() throws Exception {

        doThrow(new UsernameAlreadyExistsException("ivan")).when(authService).register(any(RegisterRequest.class));

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "ivan",
                                          "email": "ivan@example.com",
                                          "password": "plain-password",
                                          "currency": "USD"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .exists()
                );
    }

    @Test
    void registerWithDuplicateEmailReturnsConflict() throws Exception {

        doThrow(
                new EmailAlreadyExistsException(
                        "ivan@example.com"
                )
        )
                .when(authService)
                .register(any(RegisterRequest.class));

        mockMvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "ivan",
                                          "email": "ivan@example.com",
                                          "password": "plain-password",
                                          "currency": "USD"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message")
                                .exists()
                );
    }

    @Test
    void loginReturnsAccessAndRefreshTokens() throws Exception {

        Instant refreshExpiration = Instant.parse("2026-08-10T20:00:00Z");

        AuthResponse response = new AuthResponse(
                "access-token",
                "refresh-token",
                "Bearer",
                900_000L,
                refreshExpiration
        );

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "ivan",
                                          "password": "plain-password"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.accessToken")
                                .value("access-token")
                )
                .andExpect(
                        jsonPath("$.refreshToken")
                                .value("refresh-token")
                )
                .andExpect(
                        jsonPath("$.tokenType")
                                .value("Bearer")
                )
                .andExpect(
                        jsonPath("$.accessTokenExpiresIn")
                                .value(900000)
                )
                .andExpect(
                        jsonPath("$.refreshTokenExpiresAt")
                                .value(
                                        "2026-08-10T20:00:00Z"
                                )
                );

        ArgumentCaptor<LoginRequest> requestCaptor = ArgumentCaptor.forClass(LoginRequest.class);

        verify(authService).login(requestCaptor.capture());

        assertThat(requestCaptor.getValue().getUsername()).isEqualTo("ivan");

        assertThat(requestCaptor.getValue().getPassword()).isEqualTo("plain-password");
    }

    @Test
    void loginWithIncorrectCredentialsReturnsUnauthorized() throws Exception {

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(
                        new BadCredentialsException(
                                "Bad credentials"
                        )
                );

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": "ivan",
                                          "password": "wrong-password"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithInvalidRequestReturnsBadRequest() throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "username": " ",
                                          "password": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void refreshReturnsRotatedTokens() throws Exception {

        Instant refreshExpiration = Instant.parse("2026-08-10T20:00:00Z");

        AuthResponse response = new AuthResponse(
                "new-access-token",
                "new-refresh-token",
                "Bearer",
                900_000L,
                refreshExpiration
        );

        when(authService.refresh(any(RefreshTokenRequest.class))).thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": "old-refresh-token"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.accessToken")
                                .value("new-access-token")
                )
                .andExpect(
                        jsonPath("$.refreshToken")
                                .value("new-refresh-token")
                )
                .andExpect(
                        jsonPath("$.tokenType")
                                .value("Bearer")
                );

        ArgumentCaptor<RefreshTokenRequest> requestCaptor = ArgumentCaptor.forClass(RefreshTokenRequest.class);

        verify(authService).refresh(requestCaptor.capture());

        assertThat(requestCaptor.getValue().getRefreshToken()).isEqualTo("old-refresh-token");
    }

    @Test
    void refreshWithInvalidTokenReturnsUnauthorized() throws Exception {

        when(authService.refresh(any(RefreshTokenRequest.class))).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": "invalid-token"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutReturnsNoContent() throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": "refresh-token"
                                        }
                                        """)
                )
                .andExpect(status().isNoContent());

        ArgumentCaptor<RefreshTokenRequest> requestCaptor = ArgumentCaptor.forClass(RefreshTokenRequest.class);

        verify(authService).logout(requestCaptor.capture());

        assertThat(requestCaptor.getValue().getRefreshToken()).isEqualTo("refresh-token");
    }
}