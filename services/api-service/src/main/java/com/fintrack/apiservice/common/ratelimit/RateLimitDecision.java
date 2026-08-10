package com.fintrack.apiservice.common.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RateLimitDecision {

    private final boolean allowed;
    private final long currentCount;
    private final long remaining;
    private final long resetAfterSeconds;

}
