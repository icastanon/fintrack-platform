package com.fintrack.apiservice.auth.refresh.service;

import com.fintrack.apiservice.auth.refresh.dto.RefreshTokenCreationResult;
import com.fintrack.apiservice.auth.refresh.dto.RefreshTokenRotationResult;
import com.fintrack.apiservice.auth.refresh.entity.RefreshToken;
import com.fintrack.apiservice.auth.refresh.exception.InvalidRefreshTokenException;
import com.fintrack.apiservice.auth.refresh.repository.RefreshTokenRepository;
import com.fintrack.apiservice.user.entity.FintrackUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final int TOKEN_LENGTH_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpiration;
    private final SecureRandom secureRandom;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.secureRandom = new SecureRandom();
    }

    @Transactional
    public RefreshTokenCreationResult createRefreshToken(FintrackUser user) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);
        Instant expiresAt = Instant.now()
                .plusMillis(refreshTokenExpiration);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(expiresAt);

        refreshTokenRepository.save(refreshToken);

        return new RefreshTokenCreationResult(
                rawToken,
                expiresAt
        );
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawToken.getBytes(
                            java.nio.charset.StandardCharsets.UTF_8
                    )
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private String generateRawToken() {
        byte[] randomBytes =
                new byte[TOKEN_LENGTH_BYTES];

        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    @Transactional
    public RefreshTokenRotationResult rotateRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);

        RefreshToken existingToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash).orElseThrow(
                                                                InvalidRefreshTokenException::new
                                                            );

        Instant now = Instant.now();

        if (existingToken.getRevokedAt() != null || !existingToken.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }

        existingToken.setRevokedAt(now);

        String newRawToken = generateRawToken();
        String newTokenHash = hashToken(newRawToken);

        RefreshToken replacementToken = new RefreshToken();
        replacementToken.setUser(existingToken.getUser());
        replacementToken.setTokenHash(newTokenHash);

        replacementToken.setExpiresAt(existingToken.getExpiresAt());

        refreshTokenRepository.save(replacementToken);

        return new RefreshTokenRotationResult(
                existingToken.getUser().getId(),
                existingToken.getUser().getUsername(),
                existingToken.getUser().getRole(),
                newRawToken,
                replacementToken.getExpiresAt()
        );
    }

    @Transactional
    public void revokeRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);

        refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .ifPresent(refreshToken -> {
                    if (refreshToken.getRevokedAt() == null) {
                        refreshToken.setRevokedAt(Instant.now());
                    }
                });
    }
}