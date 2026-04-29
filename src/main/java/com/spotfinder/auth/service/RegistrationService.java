package com.spotfinder.auth.service;

import com.spotfinder.auth.dto.RegisterRequest;
import com.spotfinder.auth.validation.RegistrationValidator;
import com.spotfinder.user.dto.UserMapper;
import com.spotfinder.user.dto.UserResponse;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.entity.UserRole;
import com.spotfinder.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegistrationService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final RegistrationValidator registrationValidator;
  private final PasswordEncoder passwordEncoder;

  public UserResponse register(@Valid RegisterRequest registerRequest) {
    registrationValidator.validate(registerRequest);
    UserEntity entity = new UserEntity();
    entity.setEmail(registerRequest.email());
    entity.setDisplayName(registerRequest.name());
    entity.setPasswordHash(passwordEncoder.encode(registerRequest.password()));
    entity.setRole(UserRole.USER);
    entity.setEnabled(true);

    userRepository.save(entity);
    return userMapper.toResponse(entity);
  }
}
