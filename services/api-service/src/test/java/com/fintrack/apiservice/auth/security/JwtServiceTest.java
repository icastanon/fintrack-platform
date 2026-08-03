package com.fintrack.apiservice.auth.security;

import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.user.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static final String DIFFERENT_SECRET =
            "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

    private static final long EXPIRATION_MILLIS = 15 * 60 * 1000L;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_MILLIS);
    }

    @Test
    void generateTokenIncludesExpectedClaims() {
        long beforeGeneration = System.currentTimeMillis();

        String token = jwtService.generateToken(7L, "ivan", Role.USER);

        long afterGeneration = System.currentTimeMillis();

        Claims claims = parseClaims(token);

        Number userId = claims.get("userId", Number.class);

        assertThat(claims.getSubject()).isEqualTo("ivan");

        assertThat(userId.longValue()).isEqualTo(7L);

        assertThat(claims.get("role", String.class)).isEqualTo("USER");

        assertThat(claims.getIssuedAt()).isNotNull();

        assertThat(claims.getExpiration()).isNotNull();

        assertThat(claims.getIssuedAt().getTime())
                .isGreaterThanOrEqualTo(
                        beforeGeneration - 1000
                )
                .isLessThanOrEqualTo(afterGeneration);

        long actualExpirationDuration = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

        assertThat(actualExpirationDuration).isEqualTo(EXPIRATION_MILLIS);
    }

    @Test
    void extractPrincipalReturnsAuthenticatedUserPrincipal() {
        String token = jwtService.generateToken(7L, "ivan", Role.ADMIN);

        AuthenticatedUserPrincipal principal = jwtService.extractPrincipal(token);

        assertThat(principal.getUserId()).isEqualTo(7L);

        assertThat(principal.getUsername()).isEqualTo("ivan");

        assertThat(principal.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void extractUsernameReturnsTokenSubject() {
        String token = jwtService.generateToken(7L, "ivan", Role.USER);

        String username = jwtService.extractUsername(token);

        assertThat(username).isEqualTo("ivan");
    }

    @Test
    void extractPrincipalRejectsExpiredToken() {
        JwtService expiredTokenService = new JwtService(SECRET, -60_000L);

        String expiredToken = expiredTokenService.generateToken(7L, "ivan", Role.USER);

        assertThatThrownBy(() -> jwtService.extractPrincipal(expiredToken)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void extractPrincipalRejectsTokenSignedWithDifferentSecret() {
        JwtService otherJwtService = new JwtService(DIFFERENT_SECRET, EXPIRATION_MILLIS);

        String tokenSignedBySomeoneElse = otherJwtService.generateToken(7L, "ivan", Role.USER);

        assertThatThrownBy(() -> jwtService.extractPrincipal(tokenSignedBySomeoneElse)).isInstanceOf(JwtException.class);
    }

    @Test
    void extractPrincipalRejectsMalformedToken() {
        assertThatThrownBy(() -> jwtService.extractPrincipal("this-is-not-a-valid-jwt")).isInstanceOf(JwtException.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(
                        Keys.hmacShaKeyFor(
                                SECRET.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        )
                )
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}