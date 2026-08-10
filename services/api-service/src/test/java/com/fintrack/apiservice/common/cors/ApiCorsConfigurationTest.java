package com.fintrack.apiservice.common.cors;

import com.fintrack.apiservice.common.correlation.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiCorsConfigurationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    private final ApiCorsConfiguration apiCorsConfiguration = new ApiCorsConfiguration();

    @Test
    void corsConfigurationAllowsTheConfiguredOriginForApiRequests() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of(ALLOWED_ORIGIN));

        CorsConfigurationSource source = apiCorsConfiguration.corsConfigurationSource(properties);
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.OPTIONS.name(), "/api/v1/accounts");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly(ALLOWED_ORIGIN);
        assertThat(configuration.getAllowedMethods()).containsExactly(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()
        );
        assertThat(configuration.getAllowedHeaders()).containsExactly(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                CorrelationIdFilter.HEADER_NAME
        );
        assertThat(configuration.getExposedHeaders()).containsExactly(
                CorrelationIdFilter.HEADER_NAME,
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "X-RateLimit-Reset",
                HttpHeaders.RETRY_AFTER
        );
        assertThat(configuration.getAllowCredentials()).isFalse();
        assertThat(configuration.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    void corsConfigurationDoesNotApplyOutsideTheApiPath() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of(ALLOWED_ORIGIN));

        CorsConfigurationSource source = apiCorsConfiguration.corsConfigurationSource(properties);
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), "/swagger-ui/index.html");

        assertThat(source.getCorsConfiguration(request)).isNull();
    }
}
