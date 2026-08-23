package com.fintrack.apiservice.common.ratelimit;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class PublicAuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicAuthRateLimitFilter.class);

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String KEY_PREFIX = "fintrack:rate-limit:";

    private final RedisFixedWindowRateLimiter rateLimiter;
    private final JsonMapper jsonMapper;
    private final int loginLimit;
    private final Duration loginWindow;
    private final int registrationLimit;
    private final Duration registrationWindow;

    private final ClientIpAddressResolver clientIpAddressResolver;

    public PublicAuthRateLimitFilter(RedisFixedWindowRateLimiter rateLimiter,
                                     JsonMapper jsonMapper,
                                     ClientIpAddressResolver clientIpAddressResolver,
                                     int loginLimit,
                                     Duration loginWindow,
                                     int registrationLimit,
                                     Duration registrationWindow) {
        this.rateLimiter = rateLimiter;
        this.jsonMapper = jsonMapper;
        this.clientIpAddressResolver = clientIpAddressResolver;
        this.loginLimit = loginLimit;
        this.loginWindow = loginWindow;
        this.registrationLimit = registrationLimit;
        this.registrationWindow = registrationWindow;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        String requestPath = request.getRequestURI();

        return !LOGIN_PATH.equals(requestPath) && !REGISTER_PATH.equals(requestPath);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean loginRequest = LOGIN_PATH.equals(request.getRequestURI());
        String operation = loginRequest ? "login" : "registration";
        int limit = loginRequest ? loginLimit : registrationLimit;
        Duration window = loginRequest ? loginWindow : registrationWindow;
        String key = KEY_PREFIX + operation + ":" + clientIpAddressResolver.resolve(request);

        RateLimitDecision decision;

        try {
            decision = rateLimiter.evaluate(key, limit, window);
        } catch (DataAccessException exception) {
            LOGGER.warn("Redis was unavailable while evaluating the {} rate limit; allowing the request", operation);
            filterChain.doFilter(request, response);
            return;
        }

        addRateLimitHeaders(response, limit, decision);

        if (decision.isAllowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        writeTooManyRequestsResponse(response, decision);
    }

    private void addRateLimitHeaders(HttpServletResponse response, int limit, RateLimitDecision decision) {
        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        response.setHeader("X-RateLimit-Remaining", Long.toString(decision.getRemaining()));
        response.setHeader("X-RateLimit-Reset", Long.toString(decision.getResetAfterSeconds()));
    }

    private void writeTooManyRequestsResponse(HttpServletResponse response, RateLimitDecision decision) throws IOException {
        int status = HttpStatus.TOO_MANY_REQUESTS.value();
        long retryAfterSeconds = Math.max(1, decision.getResetAfterSeconds());

        ErrorResponse errorResponse = new ErrorResponse(status, "Too many requests. Try again later.", Map.of());

        response.setStatus(status);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        jsonMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}