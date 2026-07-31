package com.fintrack.apiservice.auth.service;

import com.fintrack.apiservice.auth.dto.AuthResponse;
import com.fintrack.apiservice.auth.dto.LoginRequest;
import com.fintrack.apiservice.auth.dto.RegisterRequest;
import com.fintrack.apiservice.auth.security.JwtService;
import com.fintrack.apiservice.user.entity.FintrackUser;
import com.fintrack.apiservice.user.entity.Role;
import com.fintrack.apiservice.user.exception.EmailAlreadyExistsException;
import com.fintrack.apiservice.user.exception.UsernameAlreadyExistsException;
import com.fintrack.apiservice.user.mapper.FintrackUserMapper;
import com.fintrack.apiservice.user.repository.FintrackUserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final FintrackUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FintrackUserMapper mapper;
    private final AuthenticationManager authenticationManager;
    public final JwtService jwtService;

    public AuthService(
            FintrackUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            FintrackUserMapper mapper,
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }


    @Transactional
    public void register(RegisterRequest request) {

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }

        if(userRepository.existsByEmail(request.getEmail())){
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        FintrackUser user = mapper.toEntity(request);
        user.setRole(Role.USER);

        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        System.out.println("LOGIN METHOD REACHED");

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        String token = jwtService.generateToken(
                request.getUsername()
        );

        return new AuthResponse(token);
    }
}