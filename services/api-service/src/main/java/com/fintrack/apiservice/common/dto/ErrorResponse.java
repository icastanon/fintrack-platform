package com.fintrack.apiservice.common.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class ErrorResponse {

    private int status;
    private String message;
    private Instant timestamp;
    private Map<String, String> errors;

    public ErrorResponse(int status, String message, Map<String, String> errors) {
        this.status = status;
        this.message = message;
        this.errors = errors == null ? Map.of() : errors;
        this.timestamp = Instant.now();
    }
}