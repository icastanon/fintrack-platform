package com.fintrack.apiservice.common.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class RedisFixedWindowRateLimiter {

    private static final RedisScript<String> FIXED_WINDOW_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('INCR', KEYS[1])

            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end

            local ttl = redis.call('TTL', KEYS[1])

            if ttl < 0 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
                ttl = tonumber(ARGV[1])
            end

            return tostring(current) .. ':' .. tostring(ttl)
            """,
            String.class
    );

    private final StringRedisTemplate redisTemplate;

    public RedisFixedWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RateLimitDecision evaluate(String key, int limit, Duration window) {
        validateArguments(key, limit, window);

        long windowSeconds = window.toSeconds();
        String result = redisTemplate.execute(
                FIXED_WINDOW_SCRIPT,
                List.of(key),
                Long.toString(windowSeconds)
        );

        if (result == null) {
            throw new IllegalStateException("Redis rate-limit script returned no result");
        }

        String[] parts = result.split(":");

        if (parts.length != 2) {
            throw new IllegalStateException("Redis rate-limit script returned an invalid result");
        }

        long currentCount;

        try {
            currentCount = Long.parseLong(parts[0]);
            long resetAfterSeconds = Long.parseLong(parts[1]);
            boolean allowed = currentCount <= limit;
            long remaining = Math.max(0, limit - currentCount);

            return new RateLimitDecision(
                    allowed,
                    currentCount,
                    remaining,
                    resetAfterSeconds
            );
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Redis rate-limit script returned an invalid result", exception);
        }
    }

    private void validateArguments(String key, int limit, Duration window) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Rate-limit key cannot be blank");
        }

        if (limit < 1) {
            throw new IllegalArgumentException("Rate limit must be positive");
        }

        if (window == null || window.isNegative() || window.isZero() || window.toSeconds() < 1) {
            throw new IllegalArgumentException("Rate-limit window must be at least one second");
        }
    }
}
