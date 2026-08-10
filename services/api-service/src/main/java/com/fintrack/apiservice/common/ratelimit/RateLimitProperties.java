package com.fintrack.apiservice.common.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "fintrack.rate-limit")
public class RateLimitProperties {

    private int loginLimit;
    private Duration loginWindow;
    private int registrationLimit;
    private Duration registrationWindow;

}