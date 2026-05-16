package com.spotfinder.user.service;

import com.spotfinder.common.exception.UserNotFoundException;
import com.spotfinder.user.entity.UserEntity;
import com.spotfinder.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserReader {

    private final UserRepository userRepository;

    public UserEntity getByIdOrElseThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
