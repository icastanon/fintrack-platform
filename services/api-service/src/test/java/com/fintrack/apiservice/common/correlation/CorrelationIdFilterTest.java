package com.fintrack.apiservice.common.correlation;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void validRequestCorrelationIdIsReusedThroughoutRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> correlationIdInsideChain = new AtomicReference<>();

        request.addHeader(CorrelationIdFilter.HEADER_NAME, "client-request-123");

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                correlationIdInsideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY))
        );

        assertThat(request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE)).isEqualTo("client-request-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("client-request-123");
        assertThat(correlationIdInsideChain.get()).isEqualTo("client-request-123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void missingRequestCorrelationIdGeneratesUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        String generatedCorrelationId = response.getHeader(CorrelationIdFilter.HEADER_NAME);

        assertThat(generatedCorrelationId).isNotBlank();
        assertThat(UUID.fromString(generatedCorrelationId).toString()).isEqualTo(generatedCorrelationId);
        assertThat(request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE)).isEqualTo(generatedCorrelationId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void invalidRequestCorrelationIdIsReplacedWithUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader(CorrelationIdFilter.HEADER_NAME, "invalid correlation id");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        String generatedCorrelationId = response.getHeader(CorrelationIdFilter.HEADER_NAME);

        assertThat(generatedCorrelationId).isNotEqualTo("invalid correlation id");
        assertThat(UUID.fromString(generatedCorrelationId).toString()).isEqualTo(generatedCorrelationId);
    }

    @Test
    void mdcIsClearedWhenDownstreamProcessingThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        request.addHeader(CorrelationIdFilter.HEADER_NAME, "failed-request-123");

        assertThatThrownBy(() -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("failed-request-123");
            throw new ServletException("Downstream failure");
        }))
                .isInstanceOf(ServletException.class)
                .hasMessage("Downstream failure");

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
