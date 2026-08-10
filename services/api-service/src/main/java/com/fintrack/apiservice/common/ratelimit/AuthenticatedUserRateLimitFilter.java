package com.fintrack.apiservice.common.ratelimit;

import com.fintrack.apiservice.auth.dto.AuthenticatedUserPrincipal;
import com.fintrack.apiservice.common.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class AuthenticatedUserRateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticatedUserRateLimitFilter.class);

    private static final String API_PATH_PREFIX = "/api/v1/";
    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final String KEY_PREFIX = "fintrack:rate-limit:user:";

    private final RedisFixedWindowRateLimiter rateLimiter;
    private final JsonMapper jsonMapper;
    private final int limit;
    private final Duration window;

    public AuthenticatedUserRateLimitFilter(RedisFixedWindowRateLimiter rateLimiter,
                                            JsonMapper jsonMapper,
                                            int limit,
                                            Duration window) {
        this.rateLimiter = rateLimiter;
        this.jsonMapper = jsonMapper;
        this.limit = limit;
        this.window = window;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestPath = request.getRequestURI();

        return !requestPath.startsWith(API_PATH_PREFIX) || requestPath.startsWith(AUTH_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = KEY_PREFIX + principal.getUserId();
        RateLimitDecision decision;

        try {
            decision = rateLimiter.evaluate(key, limit, window);
        } catch (DataAccessException exception) {
            LOGGER.warn("Redis was unavailable while evaluating the authenticated-user rate limit; allowing the request");
            filterChain.doFilter(request, response);
            return;
        }

        addRateLimitHeaders(response, decision);

        if (decision.isAllowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        writeTooManyRequestsResponse(response, decision);
    }

    private void addRateLimitHeaders(HttpServletResponse response, RateLimitDecision decision) {
        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.getRemaining()));
        response.setHeader("X-RateLimit-Reset", Long.toString(decision.getResetAfterSeconds()));
    }

    private void writeTooManyRequestsResponse(
            HttpServletResponse response,
            RateLimitDecision decision
    ) throws IOException {
        int status = HttpStatus.TOO_MANY_REQUESTS.value();
        long retryAfterSeconds = Math.max(1, decision.getResetAfterSeconds());

        ErrorResponse errorResponse = new ErrorResponse(
                status,
                "Too many requests. Try again later.",
                Map.of()
        );

        response.setStatus(status);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        jsonMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}