package com.fintrack.apiservice.user.service;

import com.fintrack.apiservice.common.exception.FintrackUserNotFoundException;
import com.fintrack.apiservice.user.domain.FintrackUser;
import com.fintrack.apiservice.user.dto.FintrackUserCreateRequest;
import com.fintrack.apiservice.user.dto.FintrackUserResponse;
import com.fintrack.apiservice.user.mapper.FintrackUserMapper;
import com.fintrack.apiservice.user.repository.FintrackUserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FintrackUserService {

    private final FintrackUserRepository repository;
    private final FintrackUserMapper mapper;

    public FintrackUserService(
            FintrackUserRepository repository,
            FintrackUserMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public FintrackUserResponse createUser(FintrackUserCreateRequest request) {
        FintrackUser user = mapper.toEntity(request);

        FintrackUser savedUser = repository.save(user);

        return mapper.toResponse(savedUser);
    }

    public List<FintrackUserResponse> getAllUsers() {
        return repository.findAll()
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    public FintrackUserResponse getUserById(Long id) {

        FintrackUser user = repository.findById(id)
                .orElseThrow(() -> new FintrackUserNotFoundException(id));

        return mapper.toResponse(user);
    }
}