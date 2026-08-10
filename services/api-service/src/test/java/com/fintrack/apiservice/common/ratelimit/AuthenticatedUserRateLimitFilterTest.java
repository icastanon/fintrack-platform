package com.fintrack.apiservice.common.ratelimit;

import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.user.entity.Role;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserRateLimitFilterTest {

    private static final int LIMIT = 120;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Mock
    private RedisFixedWindowRateLimiter rateLimiter;

    @Mock
    private FilterChain filterChain;

    private AuthenticatedUserRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
        filter = new AuthenticatedUserRateLimitFilter(rateLimiter, jsonMapper, LIMIT, WINDOW);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowedAuthenticatedRequestUsesUserIdKeyAndContinues() throws Exception {
        authenticate(42L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.evaluate("fintrack:rate-limit:user:42", LIMIT, WINDOW))
                .thenReturn(new RateLimitDecision(true, 21, 99, 38));

        filter.doFilter(request, response, filterChain);

        verify(rateLimiter).evaluate("fintrack:rate-limit:user:42", LIMIT, WINDOW);
        verify(filterChain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("120");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("99");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("38");
    }

    @Test
    void blockedAuthenticatedRequestReturnsTooManyRequestsWithoutContinuing() throws Exception {
        authenticate(42L);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.evaluate("fintrack:rate-limit:user:42", LIMIT, WINDOW))
                .thenReturn(new RateLimitDecision(false, 121, 0, 19));

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("19");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("120");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("19");
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"status\":429")
                .contains("\"message\":\"Too many requests. Try again later.\"");
    }

    @Test
    void requestWithoutAuthenticationContinuesWithoutCallingRedis() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(rateLimiter);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void authenticationEndpointSkipsAuthenticatedUserLimiter() throws Exception {
        authenticate(42L);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(rateLimiter);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void nonApiRequestSkipsAuthenticatedUserLimiter() throws Exception {
        authenticate(42L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(rateLimiter);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void redisFailureAllowsAuthenticatedRequestToContinue() throws Exception {
        authenticate(42L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.evaluate("fintrack:rate-limit:user:42", LIMIT, WINDOW))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
    }

    private void authenticate(Long userId) {
        AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(userId, "ivan", Role.USER);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of()
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
