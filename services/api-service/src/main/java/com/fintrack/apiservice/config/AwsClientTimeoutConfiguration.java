package com.fintrack.apiservice.config;

import io.awspring.cloud.autoconfigure.s3.S3ClientCustomizer;
import io.awspring.cloud.autoconfigure.sqs.SqsAsyncClientCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AwsClientTimeoutConfiguration {

    @Bean
    public S3ClientCustomizer s3ClientTimeoutCustomizer(
            @Value("${fintrack.aws.s3.api-call-timeout}") Duration callTimeout,
            @Value("${fintrack.aws.s3.api-call-attempt-timeout}") Duration attemptTimeout) {
        return builder -> builder.overrideConfiguration(configuration -> configuration
                .apiCallTimeout(callTimeout)
                .apiCallAttemptTimeout(attemptTimeout));
    }

    @Bean
    public SqsAsyncClientCustomizer sqsClientTimeoutCustomizer(
            @Value("${fintrack.aws.sqs.api-call-timeout}") Duration callTimeout,
            @Value("${fintrack.aws.sqs.api-call-attempt-timeout}") Duration attemptTimeout) {
        return builder -> builder.overrideConfiguration(configuration -> configuration
                .apiCallTimeout(callTimeout)
                .apiCallAttemptTimeout(attemptTimeout));
    }
}