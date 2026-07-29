package com.fintrack.apiservice.user.mapper;

import com.fintrack.apiservice.user.domain.FintrackUser;
import com.fintrack.apiservice.user.dto.FintrackUserCreateRequest;
import com.fintrack.apiservice.user.dto.FintrackUserResponse;
import org.springframework.stereotype.Component;

@Component
public class FintrackUserMapper {

    public FintrackUser toEntity(FintrackUserCreateRequest request) {
        return new FintrackUser(
                null,
                request.getUsername(),
                request.getEmail(),
                null
        );
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