package com.spotfinder.user.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spotfinder.common.exception.UserNotFoundException;
import com.spotfinder.user.dto.UpdateCurrentUserRequest;
import com.spotfinder.user.dto.UserResponse;
import com.spotfinder.user.dto.mapper.UserMapper;
import com.spotfinder.user.entity.ActivityType;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.entity.UserRole;
import com.spotfinder.user.repository.UserRepository;
import java.time.Instant;
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
    UserReader userReader;

    @Mock
    UserMapper userMapper;

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userReader, userRepository, userMapper);
    }

    @Test
    void getCurrentUser_shouldReturnUserResponse() {
        UUID userId = UUID.randomUUID();

        UserEntity user = userEntity(userId);
        UserResponse expectedResponse = userResponse(userId);

        when(userReader.getByIdOrElseThrow(userId)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(expectedResponse);

        UserResponse actualResponse = userService.getCurrentUser(userId);

        assertThat(actualResponse).isEqualTo(expectedResponse);

        verify(userReader).getByIdOrElseThrow(userId);
        verify(userMapper).toResponse(user);
    }

    @Test
    void getCurrentUser_shouldThrowExceptionWhenUserNotFound() {
        UUID userId = UUID.randomUUID();

        when(userReader.getByIdOrElseThrow(userId)).thenThrow(new UserNotFoundException(userId));

        assertThatThrownBy(() -> userService.getCurrentUser(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(userId.toString());

        verify(userReader).getByIdOrElseThrow(userId);
    }

    @Test
    void updateCurrentUser_shouldReturnUserResponse() {
        UUID userId = UUID.randomUUID();

        UserEntity user = userEntity(userId);
        UserResponse expectedResponse = userResponse(userId);
        UpdateCurrentUserRequest request = updateCurrentUserRequest();

        when(userReader.getByIdOrElseThrow(userId)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(expectedResponse);

        UserResponse actualResponse = userService.updateCurrentUser(userId, request);

        assertThat(actualResponse).isEqualTo(expectedResponse);
        assertThat(user.getDisplayName()).isEqualTo("new_user_name");
        assertThat(user.getPrimaryActivity()).isEqualTo(ActivityType.ROLLERBLADING);

        verify(userReader).getByIdOrElseThrow(userId);
        verify(userRepository).save(user);
        verify(userMapper).toResponse(user);
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
                ActivityType.BMX,
                true,
                Instant.now()
        );
    }

    private UpdateCurrentUserRequest updateCurrentUserRequest() {
        return new UpdateCurrentUserRequest(
                "new_user_name",
                ActivityType.ROLLERBLADING
        );
    }
}
