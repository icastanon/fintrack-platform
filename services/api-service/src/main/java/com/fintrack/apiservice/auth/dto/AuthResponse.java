package com.fintrack.apiservice.auth.dto;

import lombok.Data;

@Data
public class AuthResponse {

    private final String token;

    public AuthResponse(String token) {
        this.token = token;
    }
}