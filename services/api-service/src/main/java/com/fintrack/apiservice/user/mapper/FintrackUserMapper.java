package com.fintrack.apiservice.user.mapper;

import com.fintrack.apiservice.auth.dto.RegisterRequest;
import com.fintrack.apiservice.user.entity.FintrackUser;
import com.fintrack.apiservice.user.dto.FintrackUserCreateRequest;
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

        return user;
    }

    public FintrackUser toEntity(FintrackUserCreateRequest request) {

        FintrackUser user = new FintrackUser();

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setCreatedAt(LocalDateTime.now());

        return user;
    }

    public FintrackUserResponse toResponse(FintrackUser user) {
        return new FintrackUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt()
        );
    }
}