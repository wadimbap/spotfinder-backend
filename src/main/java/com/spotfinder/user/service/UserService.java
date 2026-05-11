package com.spotfinder.user.service;

import com.spotfinder.common.exception.UserNotFoundException;
import com.spotfinder.user.dto.UpdateCurrentUserRequest;
import com.spotfinder.user.dto.UserMapper;
import com.spotfinder.user.dto.UserResponse;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserResponse getCurrentUser(UUID userId) {
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        return userMapper.toResponse(user);
    }

    public UserResponse updateCurrentUser(UUID userId, UpdateCurrentUserRequest request) {
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.activityType() != null) {
            user.setPrimaryActivity(request.activityType());
        }

        UserEntity savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }
}
