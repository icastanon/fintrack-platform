package com.fintrack.apiservice.auth.refresh.dto;

import com.fintrack.apiservice.user.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class RefreshTokenRotationResult {

    private Long userId;
    private String username;
    private Role role;
    private String token;
    private Instant expiresAt;
}