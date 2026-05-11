package com.spotfinder.auth.service;

import com.spotfinder.auth.dto.AuthResponse;
import com.spotfinder.auth.dto.LoginRequest;
import com.spotfinder.auth.security.JwtService;
import com.spotfinder.common.exception.InvalidCredentialsException;
import com.spotfinder.user.dto.UserMapper;
import com.spotfinder.user.dto.UserResponse;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.repository.UserRepository;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse login(@Valid LoginRequest loginRequest) {
        String normalizedEmail = normalizeEmail(loginRequest.email());

        UserEntity user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(loginRequest.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        UserResponse userResponse = userMapper.toResponse(user);

        return new AuthResponse(accessToken, "Bearer", userResponse);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
