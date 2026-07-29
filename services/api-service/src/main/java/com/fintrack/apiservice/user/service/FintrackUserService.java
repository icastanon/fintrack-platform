package com.fintrack.apiservice.user.service;

import com.fintrack.apiservice.user.exception.FintrackUserNotFoundException;
import com.fintrack.apiservice.user.domain.FintrackUser;
import com.fintrack.apiservice.user.dto.FintrackUserCreateRequest;
import com.fintrack.apiservice.user.dto.FintrackUserResponse;
import com.fintrack.apiservice.user.dto.FintrackUserUpdateRequest;
import com.fintrack.apiservice.user.exception.UsernameAlreadyExistsException;
import com.fintrack.apiservice.user.mapper.FintrackUserMapper;
import com.fintrack.apiservice.user.repository.FintrackUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
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

    @Transactional
    public FintrackUserResponse createUser(FintrackUserCreateRequest request) {
        if (repository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }

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

    @Transactional
    public FintrackUserResponse updateUser(
            Long id,
            FintrackUserUpdateRequest request
    ) {
        FintrackUser user = repository.findById(id)
                .orElseThrow(() ->
                        new FintrackUserNotFoundException(id)
                );

        Optional<FintrackUser> optionalUser = repository.findByUsername(request.getUsername());
        if (optionalUser.isPresent() && !optionalUser.get().getId().equals(id)) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());

        return mapper.toResponse(user);
    }

    @Transactional
    public void deleteUser(Long id) {

        FintrackUser user = repository.findById(id)
                .orElseThrow(() ->
                        new FintrackUserNotFoundException(id)
                );

        repository.delete(user);
    }
}