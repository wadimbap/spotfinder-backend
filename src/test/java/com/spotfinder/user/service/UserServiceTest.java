package com.spotfinder.user.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spotfinder.common.exception.UserNotFoundException;
import com.spotfinder.user.dto.UserMapper;
import com.spotfinder.user.dto.UserResponse;
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

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    UserMapper userMapper;

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userMapper);
    }

    @Test
    void getCurrentUser_shouldReturnUserResponse() {
        UUID userId = UUID.randomUUID();

        UserEntity user = userEntity(userId);
        UserResponse expectedResponse = userResponse(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(expectedResponse);

        UserResponse actualResponse = userService.getCurrentUser(userId);

        assertThat(actualResponse).isEqualTo(expectedResponse);

        verify(userRepository).findById(userId);
        verify(userMapper).toResponse(user);
    }

    @Test
    void getCurrentUser_shouldThrowExceptionWhenUserNotFound() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(userId.toString());

        verify(userRepository).findById(userId);
    }

    private UserEntity userEntity(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setDisplayName("user_name");
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
                true,
                Instant.now()
        );
    }
}
