package com.spotfinder.auth.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spotfinder.auth.dto.AuthResponse;
import com.spotfinder.auth.dto.LoginRequest;
import com.spotfinder.auth.security.JwtService;
import com.spotfinder.common.exception.InvalidCredentialsException;
import com.spotfinder.user.dto.UserResponse;
import com.spotfinder.user.dto.mapper.UserMapper;
import com.spotfinder.user.entity.ActivityType;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.entity.UserRole;
import com.spotfinder.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String RAW_PASSWORD = "password123";
    private static final String PASSWORD_HASH = "$2a$10$hashed-password";
    private static final String ACCESS_TOKEN = "access-token";

    @Mock
    UserRepository userRepository;

    @Mock
    UserMapper userMapper;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userRepository,
                userMapper,
                passwordEncoder,
                jwtService
        );
    }

    @Test
    void login_shouldReturnAuthResponseWhenCredentialsAreValid() {
        LoginRequest request = validLoginRequest();
        UserEntity user = userEntity();
        UserResponse userResponse = userResponse(user.getId());

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name()))
                .thenReturn(ACCESS_TOKEN);
        when(userMapper.toResponse(user)).thenReturn(userResponse);

        AuthResponse actualResponse = authenticationService.login(request);

        assertThat(actualResponse.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(actualResponse.tokenType()).isEqualTo("Bearer");
        assertThat(actualResponse.user()).isEqualTo(userResponse);

        verify(userRepository).findByEmail("user@example.com");
        verify(passwordEncoder).matches(RAW_PASSWORD, PASSWORD_HASH);
        verify(jwtService).generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        verify(userMapper).toResponse(user);
    }

    @Test
    void login_shouldNormalizeEmail() {
        LoginRequest request = new LoginRequest("  User@Example.COM  ", RAW_PASSWORD);
        UserEntity user = userEntity();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn(ACCESS_TOKEN);
        when(userMapper.toResponse(user)).thenReturn(userResponse(user.getId()));

        authenticationService.login(request);

        verify(userRepository).findByEmail("user@example.com");
    }

    @Test
    void login_shouldThrowExceptionWhenEmailDoesNotExist() {
        LoginRequest request = validLoginRequest();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(userRepository).findByEmail("user@example.com");
        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtService, never()).generateAccessToken(any(), any(), any());
        verify(userMapper, never()).toResponse(any());
    }

    @Test
    void login_shouldThrowExceptionWhenPasswordIsInvalid() {
        LoginRequest request = validLoginRequest();
        UserEntity user = userEntity();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(userRepository).findByEmail("user@example.com");
        verify(passwordEncoder).matches(RAW_PASSWORD, PASSWORD_HASH);
        verify(jwtService, never()).generateAccessToken(any(), any(), any());
        verify(userMapper, never()).toResponse(any());
    }

    private LoginRequest validLoginRequest() {
        return new LoginRequest("user@example.com", RAW_PASSWORD);
    }

    private UserEntity userEntity() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setDisplayName("user_name");
        user.setPasswordHash(PASSWORD_HASH);
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        return user;
    }

    private UserResponse userResponse(UUID userId) {
        return new UserResponse(
                userId,
                "user@example.com",
                "user_name",
                UserRole.USER,
                ActivityType.BMX,
                true,
                Instant.now()
        );
    }
}
