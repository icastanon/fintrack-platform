package com.fintrack.apiservice.user.mapper;

import com.fintrack.apiservice.auth.dto.RegisterRequest;
import com.fintrack.apiservice.user.entity.FintrackUser;
import com.fintrack.apiservice.user.dto.FintrackUserResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class FintrackUserMapper {

    public FintrackUser toEntity(RegisterRequest request) {

        FintrackUser user = new FintrackUser();

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setCreatedAt(LocalDateTime.now());
        user.setCurrency(request.getCurrency());

        return user;
    }

    public FintrackUserResponse toResponse(FintrackUser user) {
        return new FintrackUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCurrency(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}