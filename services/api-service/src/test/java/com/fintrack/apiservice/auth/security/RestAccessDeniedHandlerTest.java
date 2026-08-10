package com.fintrack.apiservice.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RestAccessDeniedHandlerTest {

    private RestAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
        accessDeniedHandler = new RestAccessDeniedHandler(jsonMapper);
    }

    @Test
    void handleReturnsConsistentForbiddenResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(
                request,
                response,
                new AccessDeniedException("Access denied")
        );

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");

        assertThat(response.getContentAsString())
                .contains("\"status\":403")
                .contains("\"message\":\"You do not have permission to access this resource\"")
                .contains("\"errors\":{}")
                .containsPattern("\"timestamp\":\"[^\"]+Z\"");
    }
}