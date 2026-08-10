package com.fintrack.apiservice.common.exception;

import com.fintrack.apiservice.common.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void missingRequestParameterReturnsConsistentBadRequestResponse() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("version", "Long");

        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleMissingRequestParameter(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("Missing request parameter");
        assertThat(response.getBody().getErrors())
                .containsExactlyEntriesOf(
                        java.util.Map.of("version", "Parameter is required")
                );
        assertThat(response.getBody().getTimestamp()).isNotNull();
    }
}