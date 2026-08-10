package com.fintrack.apiservice.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RestAuthenticationEntryPointTest {

    private RestAuthenticationEntryPoint authenticationEntryPoint;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
        authenticationEntryPoint = new RestAuthenticationEntryPoint(jsonMapper);
    }

    @Test
    void commenceReturnsConsistentUnauthorizedResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        authenticationEntryPoint.commence(
                request,
                response,
                new InsufficientAuthenticationException("Authentication required")
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");

        assertThat(response.getContentAsString())
                .contains("\"status\":401")
                .contains("\"message\":\"Authentication is required\"")
                .contains("\"errors\":{}")
                .containsPattern("\"timestamp\":\"[^\"]+Z\"");
    }
}