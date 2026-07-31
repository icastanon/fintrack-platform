package com.fintrack.apiservice.user.service;

import com.fintrack.apiservice.auth.dto.FintrackUserProfileUpdateRequest;
import com.fintrack.apiservice.user.exception.EmailAlreadyExistsException;
import com.fintrack.apiservice.user.exception.FintrackUserNotFoundException;
import com.fintrack.apiservice.user.entity.FintrackUser;
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

    public FintrackUserResponse getUserByUsername(String username) {
        FintrackUser user = repository.findByUsername(username)
                .orElseThrow(() ->
                        new FintrackUserNotFoundException(username)
                );

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
    public FintrackUserResponse updateCurrentUser(
            String currentUsername,
            FintrackUserProfileUpdateRequest request
    ) {
        FintrackUser user = repository.findByUsername(currentUsername)
                .orElseThrow(() ->
                        new FintrackUserNotFoundException(currentUsername)
                );

        repository.findByEmail(request.getEmail())
                .filter(existingUser ->
                        !existingUser.getId().equals(user.getId())
                )
                .ifPresent(existingUser -> {
                    throw new EmailAlreadyExistsException(
                            request.getEmail()
                    );
                });

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