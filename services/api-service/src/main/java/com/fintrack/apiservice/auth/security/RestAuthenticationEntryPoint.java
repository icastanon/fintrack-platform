package com.fintrack.apiservice.auth.security;

import com.fintrack.apiservice.common.dto.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    public RestAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException, ServletException {
        int status = HttpStatus.UNAUTHORIZED.value();

        ErrorResponse errorResponse = new ErrorResponse(
                status,
                "Authentication is required",
                Map.of()
        );

        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        jsonMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}