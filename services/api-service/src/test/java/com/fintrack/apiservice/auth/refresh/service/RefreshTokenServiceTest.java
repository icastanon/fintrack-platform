package com.fintrack.apiservice.auth.refresh.service;

import com.fintrack.apiservice.auth.refresh.dto.RefreshTokenCreationResult;
import com.fintrack.apiservice.auth.refresh.dto.RefreshTokenRotationResult;
import com.fintrack.apiservice.auth.refresh.entity.RefreshToken;
import com.fintrack.apiservice.auth.refresh.exception.InvalidRefreshTokenException;
import com.fintrack.apiservice.auth.refresh.repository.RefreshTokenRepository;
import com.fintrack.apiservice.user.entity.FintrackUser;
import com.fintrack.apiservice.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final long REFRESH_TOKEN_EXPIRATION = 7L * 24 * 60 * 60 * 1000;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Captor
    private ArgumentCaptor<RefreshToken> refreshTokenCaptor;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                REFRESH_TOKEN_EXPIRATION
        );
    }

    @Test
    void createRefreshTokenStoresHashAndReturnsRawToken() {
        FintrackUser user = createUser();

        when(refreshTokenRepository.save(
                any(RefreshToken.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        Instant beforeCreation = Instant.now();

        RefreshTokenCreationResult result = refreshTokenService.createRefreshToken(user);

        Instant afterCreation = Instant.now();

        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());

        RefreshToken savedToken = refreshTokenCaptor.getValue();

        assertThat(result.getToken()).isNotBlank().doesNotContain("=");

        byte[] decodedToken = Base64.getUrlDecoder().decode(result.getToken());

        assertThat(decodedToken).hasSize(32);

        assertThat(savedToken.getUser()).isSameAs(user);

        assertThat(savedToken.getTokenHash())
                .isEqualTo(
                        refreshTokenService.hashToken(
                                result.getToken()
                        )
                )
                .hasSize(64)
                .isNotEqualTo(result.getToken());

        assertThat(savedToken.getExpiresAt()).isEqualTo(result.getExpiresAt());

        assertThat(result.getExpiresAt())
                .isAfterOrEqualTo(
                        beforeCreation.plusMillis(
                                REFRESH_TOKEN_EXPIRATION
                        )
                )
                .isBeforeOrEqualTo(
                        afterCreation.plusMillis(
                                REFRESH_TOKEN_EXPIRATION
                        )
                );
    }

    @Test
    void rotateRefreshTokenRevokesOldTokenAndCreatesReplacement() {
        FintrackUser user = createUser();

        String oldRawToken = "old-refresh-token";
        String oldTokenHash = refreshTokenService.hashToken(oldRawToken);

        Instant originalExpiration = Instant.now().plusSeconds(3600);

        RefreshToken existingToken = new RefreshToken();
        existingToken.setUser(user);
        existingToken.setTokenHash(oldTokenHash);
        existingToken.setExpiresAt(originalExpiration);

        when(refreshTokenRepository.findByTokenHashForUpdate(oldTokenHash)).thenReturn(Optional.of(existingToken));

        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Instant beforeRotation = Instant.now();

        RefreshTokenRotationResult result = refreshTokenService.rotateRefreshToken(oldRawToken);

        Instant afterRotation = Instant.now();

        assertThat(existingToken.getRevokedAt())
                .isNotNull()
                .isAfterOrEqualTo(beforeRotation)
                .isBeforeOrEqualTo(afterRotation);

        verify(refreshTokenRepository).save(refreshTokenCaptor.capture());

        RefreshToken replacementToken = refreshTokenCaptor.getValue();

        assertThat(replacementToken.getUser()).isSameAs(user);

        assertThat(replacementToken.getExpiresAt()).isEqualTo(originalExpiration);

        assertThat(replacementToken.getTokenHash())
                .isEqualTo(
                        refreshTokenService.hashToken(
                                result.getToken()
                        )
                )
                .isNotEqualTo(oldTokenHash);

        assertThat(result.getToken()).isNotBlank().isNotEqualTo(oldRawToken);

        assertThat(result.getUserId()).isEqualTo(7L);

        assertThat(result.getUsername()).isEqualTo("ivan");

        assertThat(result.getRole()).isEqualTo(Role.USER);

        assertThat(result.getExpiresAt()).isEqualTo(originalExpiration);

        verify(refreshTokenRepository).findByTokenHashForUpdate(oldTokenHash);
    }

    @Test
    void rotateRefreshTokenRejectsUnknownToken() {
        String rawToken = "unknown-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                refreshTokenService.rotateRefreshToken(rawToken)
        )
                .isInstanceOf(
                        InvalidRefreshTokenException.class
                )
                .hasMessage(
                        "Refresh token is invalid or expired"
                );

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void rotateRefreshTokenRejectsExpiredToken() {
        String rawToken = "expired-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setUser(createUser());
        expiredToken.setTokenHash(tokenHash);
        expiredToken.setExpiresAt(Instant.now().minusSeconds(1));

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(expiredToken.getRevokedAt()).isNull();

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void rotateRefreshTokenRejectsRevokedToken() {
        String rawToken = "revoked-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        Instant originalRevocationTime = Instant.now().minusSeconds(60);

        RefreshToken revokedToken = new RefreshToken();
        revokedToken.setUser(createUser());
        revokedToken.setTokenHash(tokenHash);
        revokedToken.setExpiresAt(Instant.now().plusSeconds(3600));
        revokedToken.setRevokedAt(originalRevocationTime);

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken(rawToken)
        ).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(revokedToken.getRevokedAt()).isEqualTo(originalRevocationTime);

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void revokeRefreshTokenRevokesActiveToken() {
        String rawToken = "active-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken activeToken = new RefreshToken();
        activeToken.setTokenHash(tokenHash);
        activeToken.setExpiresAt(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(activeToken));

        Instant beforeRevocation = Instant.now();

        refreshTokenService.revokeRefreshToken(rawToken);

        Instant afterRevocation = Instant.now();

        assertThat(activeToken.getRevokedAt())
                .isNotNull()
                .isAfterOrEqualTo(beforeRevocation)
                .isBeforeOrEqualTo(afterRevocation);

        verify(refreshTokenRepository).findByTokenHashForUpdate(tokenHash);
    }

    @Test
    void revokeRefreshTokenDoesNotChangeAlreadyRevokedToken() {
        String rawToken = "already-revoked-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        Instant originalRevocationTime = Instant.now().minusSeconds(60);

        RefreshToken revokedToken = new RefreshToken();
        revokedToken.setTokenHash(tokenHash);
        revokedToken.setRevokedAt(originalRevocationTime);

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(revokedToken));

        refreshTokenService.revokeRefreshToken(rawToken);

        assertThat(revokedToken.getRevokedAt()).isEqualTo(originalRevocationTime);
    }

    @Test
    void revokeRefreshTokenIgnoresUnknownToken() {
        String rawToken = "unknown-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.empty());

        refreshTokenService.revokeRefreshToken(rawToken);

        verify(refreshTokenRepository).findByTokenHashForUpdate(tokenHash);
    }

    private FintrackUser createUser() {
        FintrackUser user = new FintrackUser();
        user.setId(7L);
        user.setUsername("ivan");
        user.setRole(Role.USER);

        return user;
    }
}