package com.spotfinder.auth.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spotfinder.auth.dto.AuthResponse;
import com.spotfinder.auth.dto.RegisterRequest;
import com.spotfinder.auth.security.JwtService;
import com.spotfinder.auth.validation.RegistrationValidator;
import com.spotfinder.common.exception.EmailAlreadyExistsException;
import com.spotfinder.common.exception.PasswordConfirmationMismatchException;
import com.spotfinder.user.dto.UserMapper;
import com.spotfinder.user.dto.UserResponse;
import com.spotfinder.user.entity.ActivityType;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.entity.UserRole;
import com.spotfinder.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

  private static final String RAW_PASSWORD = "password123";
  private static final String ENCODED_PASSWORD = "$2a$10$hashed-password";
  private static final String ACCESS_TOKEN = "access-token";

  @Mock
  UserRepository userRepository;

  @Mock
  UserMapper userMapper;

  @Mock
  RegistrationValidator registrationValidator;

  @Mock
  PasswordEncoder passwordEncoder;

  @Mock
  JwtService jwtService;

  RegistrationService registrationService;

  @BeforeEach
  void setUp() {
    registrationService = new RegistrationService(
            userRepository,
            userMapper,
            registrationValidator,
            jwtService,
            passwordEncoder
    );
  }

  @Test
  void register_shouldCreateUserAndReturnAuthResponse() {
    RegisterRequest request = validRegisterRequest();
    UserResponse userResponse = userResponse();

    when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
    when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userMapper.toResponse(any(UserEntity.class))).thenReturn(userResponse);
    when(jwtService.generateAccessToken(any(), any(), any())).thenReturn(ACCESS_TOKEN);

    AuthResponse actualResponse = registrationService.register(request);

    assertThat(actualResponse.accessToken()).isEqualTo(ACCESS_TOKEN);
    assertThat(actualResponse.tokenType()).isEqualTo("Bearer");
    assertThat(actualResponse.user()).isEqualTo(userResponse);

    ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userCaptor.capture());

    UserEntity userToSave = userCaptor.getValue();

    assertThat(userToSave.getEmail()).isEqualTo("user@example.com");
    assertThat(userToSave.getDisplayName()).isEqualTo("user_name");
    assertThat(userToSave.getPasswordHash()).isEqualTo(ENCODED_PASSWORD);
    assertThat(userToSave.getPasswordHash()).isNotEqualTo(RAW_PASSWORD);
    assertThat(userToSave.getRole()).isEqualTo(UserRole.USER);
    assertThat(userToSave.getEnabled()).isTrue();

    verify(registrationValidator).validate(request);
    verify(passwordEncoder).encode(RAW_PASSWORD);
    verify(userMapper).toResponse(userToSave);
    verify(jwtService).generateAccessToken(
            userToSave.getId(),
            userToSave.getEmail(),
            userToSave.getRole().name()
    );
  }

  @Test
  void register_shouldNormalizeEmail() {
    RegisterRequest request = validRegisterRequest();

    when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
    when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userMapper.toResponse(any(UserEntity.class))).thenReturn(userResponse());
    when(jwtService.generateAccessToken(any(), any(), any())).thenReturn(ACCESS_TOKEN);

    registrationService.register(request);

    ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userCaptor.capture());

    assertThat(userCaptor.getValue().getEmail()).isEqualTo("user@example.com");
  }

  @Test
  void register_shouldTrimDisplayName() {
    RegisterRequest request =
            new RegisterRequest("user@example.com", "  user_name  ", RAW_PASSWORD, RAW_PASSWORD);

    when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
    when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userMapper.toResponse(any(UserEntity.class))).thenReturn(userResponse());
    when(jwtService.generateAccessToken(any(), any(), any())).thenReturn(ACCESS_TOKEN);

    registrationService.register(request);

    ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userCaptor.capture());

    assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("user_name");
  }

  @Test
  void register_shouldThrowExceptionWhenEmailAlreadyExists() {
    RegisterRequest request = validRegisterRequest();

    doThrow(new EmailAlreadyExistsException("user@example.com"))
            .when(registrationValidator)
            .validate(request);

    assertThatThrownBy(() -> registrationService.register(request))
            .isInstanceOf(EmailAlreadyExistsException.class)
            .hasMessageContaining("user@example.com");

    verify(userRepository, never()).save(any(UserEntity.class));
    verify(passwordEncoder, never()).encode(any());
    verify(userMapper, never()).toResponse(any(UserEntity.class));
    verify(jwtService, never()).generateAccessToken(any(), any(), any());
  }

  @Test
  void register_shouldThrowExceptionWhenPasswordConfirmationDoesNotMatch() {
    RegisterRequest request =
            new RegisterRequest("user@example.com", "user_name", RAW_PASSWORD, "different-password");

    doThrow(new PasswordConfirmationMismatchException("Password should match"))
            .when(registrationValidator)
            .validate(request);

    assertThatThrownBy(() -> registrationService.register(request))
            .isInstanceOf(PasswordConfirmationMismatchException.class);

    verify(userRepository, never()).save(any(UserEntity.class));
    verify(passwordEncoder, never()).encode(any());
    verify(userMapper, never()).toResponse(any(UserEntity.class));
    verify(jwtService, never()).generateAccessToken(any(), any(), any());
  }

  @Test
  void register_shouldReturnMappedUserInsideAuthResponse() {
    RegisterRequest request = validRegisterRequest();
    UserResponse expectedUserResponse = userResponse();

    when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED_PASSWORD);
    when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userMapper.toResponse(any(UserEntity.class))).thenReturn(expectedUserResponse);
    when(jwtService.generateAccessToken(any(), any(), any())).thenReturn(ACCESS_TOKEN);

    AuthResponse actualResponse = registrationService.register(request);

    assertThat(actualResponse.user()).isEqualTo(expectedUserResponse);
    assertThat(actualResponse.accessToken()).isEqualTo(ACCESS_TOKEN);
    assertThat(actualResponse.tokenType()).isEqualTo("Bearer");

    verify(userMapper).toResponse(any(UserEntity.class));
  }

  private RegisterRequest validRegisterRequest() {
    return new RegisterRequest("  User@example.com", "user_name", RAW_PASSWORD, RAW_PASSWORD);
  }

  private UserResponse userResponse() {
    return new UserResponse(
            UUID.randomUUID(),
            "user@example.com",
            "user_name",
            UserRole.USER,
            ActivityType.BMX,
            true,
            Instant.now()
    );
  }
}
