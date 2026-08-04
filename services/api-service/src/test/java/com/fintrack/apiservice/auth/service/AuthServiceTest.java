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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private FintrackUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private FintrackUserMapper mapper;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerCreatesUserWithUserRoleAndEncodedPassword() {
        RegisterRequest request = mock(RegisterRequest.class);
        FintrackUser user = new FintrackUser();

        when(request.getUsername()).thenReturn("ivan");
        when(request.getEmail()).thenReturn("ivan@example.com");
        when(request.getPassword()).thenReturn("plain-password");

        when(userRepository.existsByUsername("ivan")).thenReturn(false);
        when(userRepository.existsByEmail("ivan@example.com")).thenReturn(false);
        when(mapper.toEntity(request)).thenReturn(user);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");

        authService.register(request);

        ArgumentCaptor<FintrackUser> userCaptor = ArgumentCaptor.forClass(FintrackUser.class);

        verify(userRepository).saveAndFlush(userCaptor.capture());

        FintrackUser savedUser = userCaptor.getValue();

        assertThat(savedUser).isSameAs(user);
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");

        verify(passwordEncoder).encode("plain-password");
    }

    @Test
    void registerRejectsDuplicateUsername() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("ivan");
        request.setEmail("ivan@example.com");
        request.setPassword("password");

        when(userRepository.existsByUsername("ivan")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(UsernameAlreadyExistsException.class);

        verify(userRepository, never()).existsByEmail(any());

        verifyNoInteractions(mapper, passwordEncoder);

        verify(userRepository, never()).save(any(FintrackUser.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("ivan");
        request.setEmail("ivan@example.com");
        request.setPassword("password");

        when(userRepository.existsByUsername("ivan")).thenReturn(false);

        when(userRepository.existsByEmail("ivan@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(EmailAlreadyExistsException.class);

        verifyNoInteractions(mapper, passwordEncoder);

        verify(userRepository, never()).save(any(FintrackUser.class));
    }

    @Test
    void loginAuthenticatesCredentialsAndReturnsTokens() {
        LoginRequest request = new LoginRequest();
        request.setUsername("ivan");
        request.setPassword("plain-password");

        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);

        when(authentication.getName()).thenReturn("ivan");

        FintrackUser user = createUser();

        when(userRepository.findByUsername("ivan")).thenReturn(Optional.of(user));

        when(jwtService.generateToken(7L, "ivan", Role.USER)).thenReturn("access-token");

        Instant refreshExpiration = Instant.now().plusSeconds(3600);

        when(refreshTokenService.createRefreshToken(user))
                .thenReturn(
                        new RefreshTokenCreationResult(
                                "refresh-token",
                                refreshExpiration
                        )
                );

        when(jwtService.getExpiration()).thenReturn(900_000L);

        AuthResponse response = authService.login(request);

        ArgumentCaptor<UsernamePasswordAuthenticationToken> authenticationCaptor =
                ArgumentCaptor.forClass(
                        UsernamePasswordAuthenticationToken.class
                );

        verify(authenticationManager).authenticate(authenticationCaptor.capture());

        UsernamePasswordAuthenticationToken credentials = authenticationCaptor.getValue();

        assertThat(credentials.getPrincipal()).isEqualTo("ivan");

        assertThat(credentials.getCredentials()).isEqualTo("plain-password");

        assertThat(response.getAccessToken()).isEqualTo("access-token");

        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");

        assertThat(response.getTokenType()).isEqualTo("Bearer");

        assertThat(response.getAccessTokenExpiresIn()).isEqualTo(900_000L);

        assertThat(response.getRefreshTokenExpiresAt()).isEqualTo(refreshExpiration);
    }

    @Test
    void loginDoesNotIssueTokensWhenCredentialsAreInvalid() {
        LoginRequest request = new LoginRequest();
        request.setUsername("ivan");
        request.setPassword("wrong-password");

        when(authenticationManager.authenticate(
                any(UsernamePasswordAuthenticationToken.class)
        )).thenThrow(
                new BadCredentialsException("Bad credentials")
        );

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(jwtService, refreshTokenService);

        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void refreshRotatesRefreshTokenAndReturnsNewAccessToken() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh-token");

        Instant originalExpiration = Instant.now().plusSeconds(3600);

        RefreshTokenRotationResult rotationResult =
                new RefreshTokenRotationResult(
                        7L,
                        "ivan",
                        Role.USER,
                        "new-refresh-token",
                        originalExpiration
                );

        when(refreshTokenService.rotateRefreshToken("old-refresh-token")).thenReturn(rotationResult);

        when(jwtService.generateToken(7L, "ivan", Role.USER)).thenReturn("new-access-token");

        when(jwtService.getExpiration()).thenReturn(900_000L);

        AuthResponse response = authService.refresh(request);

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");

        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");

        assertThat(response.getTokenType()).isEqualTo("Bearer");

        assertThat(response.getAccessTokenExpiresIn()).isEqualTo(900_000L);

        assertThat(response.getRefreshTokenExpiresAt()).isEqualTo(originalExpiration);

        verify(refreshTokenService).rotateRefreshToken("old-refresh-token");

        verify(jwtService).generateToken(7L, "ivan", Role.USER);
    }

    @Test
    void logoutRevokesSuppliedRefreshToken() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh-token");

        authService.logout(request);

        verify(refreshTokenService).revokeRefreshToken("refresh-token");

        verifyNoInteractions(jwtService);
    }

    private FintrackUser createUser() {
        FintrackUser user = new FintrackUser();
        user.setId(7L);
        user.setUsername("ivan");
        user.setEmail("ivan@example.com");
        user.setRole(Role.USER);
        user.setPasswordHash("encoded-password");

        return user;
    }
}