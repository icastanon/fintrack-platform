package com.fintrack.apiservice.common.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration {

    @Bean
    public FilterRegistrationBean<PublicAuthRateLimitFilter> publicAuthRateLimitFilter(RedisFixedWindowRateLimiter rateLimiter,
                                                                                       JsonMapper jsonMapper,
                                                                                       ClientIpAddressResolver clientIpAddressResolver,
                                                                                       RateLimitProperties properties) {
        PublicAuthRateLimitFilter filter = new PublicAuthRateLimitFilter(
                rateLimiter,
                jsonMapper,
                clientIpAddressResolver,
                properties.getLoginLimit(),
                properties.getLoginWindow(),
                properties.getRegistrationLimit(),
                properties.getRegistrationWindow()
        );

        FilterRegistrationBean<PublicAuthRateLimitFilter> registration = new FilterRegistrationBean<>(filter);

        registration.setName("publicAuthRateLimitFilter");
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);

        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuthenticatedUserRateLimitFilter> authenticatedUserRateLimitFilter(RedisFixedWindowRateLimiter rateLimiter,
                                                                                                     JsonMapper jsonMapper,
                                                                                                     RateLimitProperties properties) {
        AuthenticatedUserRateLimitFilter filter = new AuthenticatedUserRateLimitFilter(
                rateLimiter,
                jsonMapper,
                properties.getAuthenticatedUserLimit(),
                properties.getAuthenticatedUserWindow()
        );

        FilterRegistrationBean<AuthenticatedUserRateLimitFilter> registration = new FilterRegistrationBean<>(filter);

        registration.setName("authenticatedUserRateLimitFilter");
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);

        return registration;
    }

    @Bean
    public FilterRegistrationBean<ImportSubmissionRateLimitFilter> importSubmissionRateLimitFilter(RedisFixedWindowRateLimiter rateLimiter,
                                                                                                   JsonMapper jsonMapper,
                                                                                                   RateLimitProperties properties) {
        ImportSubmissionRateLimitFilter filter = new ImportSubmissionRateLimitFilter(
                rateLimiter,
                jsonMapper,
                properties.getImportSubmissionLimit(),
                properties.getImportSubmissionWindow()
        );

        FilterRegistrationBean<ImportSubmissionRateLimitFilter> registration = new FilterRegistrationBean<>(filter);

        registration.setName("importSubmissionRateLimitFilter");
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 2);

        return registration;
    }
}