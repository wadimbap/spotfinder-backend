package com.spotfinder.user.service;

import com.spotfinder.user.dto.UpdateCurrentUserRequest;
import com.spotfinder.user.dto.UserResponse;
import com.spotfinder.user.dto.mapper.UserMapper;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserReader userReader;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserResponse getCurrentUser(UUID userId) {
        UserEntity user = userReader.getByIdOrElseThrow(userId);
        return userMapper.toResponse(user);
    }

    public UserResponse updateCurrentUser(UUID userId, UpdateCurrentUserRequest request) {
        UserEntity user = userReader.getByIdOrElseThrow(userId);;

        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.primaryActivity() != null) {
            user.setPrimaryActivity(request.primaryActivity());
        }

        UserEntity savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }
}
