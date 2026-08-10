package com.fintrack.apiservice.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisFixedWindowRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private RedisFixedWindowRateLimiter rateLimiter;

    @Test
    void evaluateAllowsRequestBelowLimit() {
        String key = "fintrack:rate-limit:login:127.0.0.1";

        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<String>>any(),
                ArgumentMatchers.eq(List.of(key)),
                ArgumentMatchers.eq("60")
        )).thenReturn("3:47");

        RateLimitDecision result = rateLimiter.evaluate(key, 5, Duration.ofMinutes(1));

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getCurrentCount()).isEqualTo(3);
        assertThat(result.getRemaining()).isEqualTo(2);
        assertThat(result.getResetAfterSeconds()).isEqualTo(47);

        verify(redisTemplate).execute(
                ArgumentMatchers.<RedisScript<String>>any(),
                ArgumentMatchers.eq(List.of(key)),
                ArgumentMatchers.eq("60")
        );
    }

    @Test
    void evaluateAllowsRequestAtLimit() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<String>>any(),
                ArgumentMatchers.anyList(),
                ArgumentMatchers.eq("60")
        )).thenReturn("5:32");

        RateLimitDecision result = rateLimiter.evaluate(
                "fintrack:rate-limit:login:127.0.0.1",
                5,
                Duration.ofMinutes(1)
        );

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getRemaining()).isZero();
    }

    @Test
    void evaluateRejectsRequestAboveLimit() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<String>>any(),
                ArgumentMatchers.anyList(),
                ArgumentMatchers.eq("60")
        )).thenReturn("6:28");

        RateLimitDecision result = rateLimiter.evaluate(
                "fintrack:rate-limit:login:127.0.0.1",
                5,
                Duration.ofMinutes(1)
        );

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getCurrentCount()).isEqualTo(6);
        assertThat(result.getRemaining()).isZero();
        assertThat(result.getResetAfterSeconds()).isEqualTo(28);
    }

    @Test
    void evaluateThrowsWhenRedisReturnsNoResult() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<String>>any(),
                ArgumentMatchers.anyList(),
                ArgumentMatchers.eq("60")
        )).thenReturn(null);

        assertThatThrownBy(() ->
                rateLimiter.evaluate(
                        "fintrack:rate-limit:login:127.0.0.1",
                        5,
                        Duration.ofMinutes(1)
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis rate-limit script returned no result");
    }

    @Test
    void evaluateThrowsWhenRedisReturnsMalformedResult() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<String>>any(),
                ArgumentMatchers.anyList(),
                ArgumentMatchers.eq("60")
        )).thenReturn("invalid");

        assertThatThrownBy(() ->
                rateLimiter.evaluate(
                        "fintrack:rate-limit:login:127.0.0.1",
                        5,
                        Duration.ofMinutes(1)
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis rate-limit script returned an invalid result");
    }

    @Test
    void evaluateRejectsInvalidArgumentsBeforeCallingRedis() {
        assertThatThrownBy(() ->
                rateLimiter.evaluate(" ", 5, Duration.ofMinutes(1))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rate-limit key cannot be blank");

        assertThatThrownBy(() ->
                rateLimiter.evaluate("key", 0, Duration.ofMinutes(1))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rate limit must be positive");

        assertThatThrownBy(() ->
                rateLimiter.evaluate("key", 5, Duration.ofMillis(500))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rate-limit window must be at least one second");
    }
}