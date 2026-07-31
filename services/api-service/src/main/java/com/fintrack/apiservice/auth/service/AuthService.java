package com.fintrack.apiservice.auth.service;

import com.fintrack.apiservice.auth.dto.AuthResponse;
import com.fintrack.apiservice.auth.dto.LoginRequest;
import com.fintrack.apiservice.auth.dto.RegisterRequest;
import com.fintrack.apiservice.auth.refresh.dto.RefreshTokenCreationResult;
import com.fintrack.apiservice.auth.refresh.dto.RefreshTokenRequest;
import com.fintrack.apiservice.auth.refresh.dto.RefreshTokenRotationResult;
import com.fintrack.apiservice.auth.refresh.service.RefreshTokenService;
import com.fintrack.apiservice.auth.security.JwtService;
import com.fintrack.apiservice.user.entity.FintrackUser;
import com.fintrack.apiservice.user.entity.Role;
import com.fintrack.apiservice.user.exception.EmailAlreadyExistsException;
import com.fintrack.apiservice.user.exception.UsernameAlreadyExistsException;
import com.fintrack.apiservice.user.mapper.FintrackUserMapper;
import com.fintrack.apiservice.user.repository.FintrackUserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    public final RefreshTokenService refreshTokenService;

    public AuthService(
            FintrackUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            FintrackUserMapper mapper,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
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

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        String username = authentication.getName();

        FintrackUser user = userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException(username)
                );

        String accessToken = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole());

        RefreshTokenCreationResult refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(accessToken, refreshToken.getToken(), "Bearer", jwtService.getExpiration(),
                refreshToken.getExpiresAt()
        );
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenRotationResult rotationResult = refreshTokenService.rotateRefreshToken(request.getRefreshToken());

        String accessToken = jwtService.generateToken(rotationResult.getUserId(), rotationResult.getUsername(), rotationResult.getRole());

        return new AuthResponse(
                accessToken,
                rotationResult.getToken(),
                "Bearer",
                jwtService.getExpiration(),
                rotationResult.getExpiresAt()
        );
    }

    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revokeRefreshToken(request.getRefreshToken());
    }
}