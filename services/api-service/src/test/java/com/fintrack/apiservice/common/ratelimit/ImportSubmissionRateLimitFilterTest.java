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
class ImportSubmissionRateLimitFilterTest {

    private static final int LIMIT = 5;
    private static final Duration WINDOW = Duration.ofHours(1);

    @Mock
    private RedisFixedWindowRateLimiter rateLimiter;

    @Mock
    private FilterChain filterChain;

    private ImportSubmissionRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
        filter = new ImportSubmissionRateLimitFilter(rateLimiter, jsonMapper, LIMIT, WINDOW);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowedImportSubmissionUsesUserIdKeyAndContinues() throws Exception {
        authenticate(42L);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/imports");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.evaluate("fintrack:rate-limit:import:user:42", LIMIT, WINDOW))
                .thenReturn(new RateLimitDecision(true, 2, 3, 2700));

        filter.doFilter(request, response, filterChain);

        verify(rateLimiter).evaluate("fintrack:rate-limit:import:user:42", LIMIT, WINDOW);
        verify(filterChain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("3");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("2700");
    }

    @Test
    void blockedImportSubmissionReturnsTooManyRequestsWithoutContinuing() throws Exception {
        authenticate(42L);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/imports");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.evaluate("fintrack:rate-limit:import:user:42", LIMIT, WINDOW))
                .thenReturn(new RateLimitDecision(false, 6, 0, 1800));

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("1800");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("1800");
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"status\":429")
                .contains("\"message\":\"Too many transaction import submissions. Try again later.\"");
    }

    @Test
    void getImportStatusDoesNotUseImportSubmissionLimiter() throws Exception {
        authenticate(42L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/imports/41");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(rateLimiter);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void unrelatedPostRequestDoesNotUseImportSubmissionLimiter() throws Exception {
        authenticate(42L);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transactions");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(rateLimiter);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void unauthenticatedImportSubmissionContinuesWithoutCallingRedis() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/imports");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(rateLimiter);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void redisFailureAllowsImportSubmissionToContinue() throws Exception {
        authenticate(42L);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/imports");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.evaluate("fintrack:rate-limit:import:user:42", LIMIT, WINDOW))
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