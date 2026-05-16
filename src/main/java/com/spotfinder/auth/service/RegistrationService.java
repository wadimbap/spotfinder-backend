package com.spotfinder.auth.service;

import com.spotfinder.auth.dto.AuthResponse;
import com.spotfinder.auth.dto.RegisterRequest;
import com.spotfinder.auth.security.JwtService;
import com.spotfinder.auth.validation.RegistrationValidator;
import com.spotfinder.user.dto.UserResponse;
import com.spotfinder.user.dto.mapper.UserMapper;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.entity.UserRole;
import com.spotfinder.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegistrationService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final RegistrationValidator registrationValidator;
  private final JwtService jwtService;
  private final PasswordEncoder passwordEncoder;

  public AuthResponse register(RegisterRequest registerRequest) {
    registrationValidator.validate(registerRequest);

    UserEntity user = new UserEntity();
    user.setEmail(registerRequest.email().trim().toLowerCase());
    user.setDisplayName(registerRequest.name());
    user.setPasswordHash(passwordEncoder.encode(registerRequest.password()));
    user.setRole(UserRole.USER);
    user.setEnabled(true);

    UserEntity savedUser = userRepository.save(user);

    String accessToken = jwtService.generateAccessToken(
            savedUser.getId(),
            savedUser.getEmail(),
            savedUser.getRole().name()
    );

    UserResponse userResponse = userMapper.toResponse(savedUser);

    return new AuthResponse(
            accessToken,
            "Bearer",
            userResponse
    );
  }
}
