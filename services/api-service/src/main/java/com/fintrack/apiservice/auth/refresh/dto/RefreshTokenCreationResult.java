package com.fintrack.apiservice.auth.refresh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class RefreshTokenCreationResult {
    private final String token;
    private final Instant expiresAt;
}