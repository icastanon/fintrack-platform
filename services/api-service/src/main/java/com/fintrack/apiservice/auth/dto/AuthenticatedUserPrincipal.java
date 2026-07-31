package com.fintrack.apiservice.auth.dto;

import com.fintrack.apiservice.user.entity.Role;
import lombok.*;
import org.springframework.security.core.AuthenticatedPrincipal;

@Data
@AllArgsConstructor
public class AuthenticatedUserPrincipal implements AuthenticatedPrincipal {

    private final Long userId;
    private final String username;
    private final Role role;

    @Override
    public String getName() {
        return username;
    }
}