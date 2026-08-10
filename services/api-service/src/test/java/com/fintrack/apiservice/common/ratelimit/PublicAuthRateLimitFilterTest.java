package com.fintrack.apiservice.common.ratelimit;

import jakarta.servlet.FilterChain;
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
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicAuthRateLimitFilterTest {

    private static final int LOGIN_LIMIT = 10;
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(1);
    private static final int REGISTRATION_LIMIT = 5;
    private static final Duration REGISTRATION_WINDOW = Duration.ofHours(1);

    @Mock
    private RedisFixedWindowRateLimiter rateLimiter;

    @Mock
    private FilterChain filterChain;

    private PublicAuthRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

        filter = new PublicAuthRateLimitFilter(
                rateLimiter,
                jsonMapper,
                LOGIN_LIMIT,
                LOGIN_WINDOW,
                REGISTRATION_LIMIT,
                REGISTRATION_WINDOW
        );
    }

    @Test
    void allowedLoginRequestContinuesWithRateLimitHeaders() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/auth/login", "203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.evaluate(
                "fintrack:rate-limit:login:203.0.113.10",
                LOGIN_LIMIT,
                LOGIN_WINDOW
        )).thenReturn(new RateLimitDecision(true, 3, 7, 42));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("7");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("42");
    }

    @Test
    void blockedLoginRequestReturnsTooManyRequestsWithoutContinuing() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/auth/login", "203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.evaluate(
                "fintrack:rate-limit:login:203.0.113.10",
                LOGIN_LIMIT,
                LOGIN_WINDOW
        )).thenReturn(new RateLimitDecision(false, 11, 0, 27));

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(filterChain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("27");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("27");
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"status\":429")
                .contains("\"message\":\"Too many requests. Try again later.\"");
    }

    @Test
    void registrationRequestUsesSeparateKeyAndPolicy() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/auth/register", "198.51.100.25");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.evaluate(
                "fintrack:rate-limit:registration:198.51.100.25",
                REGISTRATION_LIMIT,
                REGISTRATION_WINDOW
        )).thenReturn(new RateLimitDecision(true, 1, 4, 3600));

        filter.doFilter(request, response, filterChain);

        verify(rateLimiter).evaluate(
                "fintrack:rate-limit:registration:198.51.100.25",
                REGISTRATION_LIMIT,
                REGISTRATION_WINDOW
        );
        verify(filterChain).doFilter(request, response);
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
    }

    @Test
    void unrelatedPostRequestSkipsRateLimiter() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/transactions", "203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(rateLimiter);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void nonPostLoginRequestSkipsRateLimiter() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/auth/login", "203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(rateLimiter);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void redisFailureAllowsRequestToContinue() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/auth/login", "203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.evaluate(
                "fintrack:rate-limit:login:203.0.113.10",
                LOGIN_LIMIT,
                LOGIN_WINDOW
        )).thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
    }

    private MockHttpServletRequest request(String method, String path, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
