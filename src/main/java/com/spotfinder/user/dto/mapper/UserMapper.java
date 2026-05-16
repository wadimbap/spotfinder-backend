package com.spotfinder.user.dto.mapper;

import com.spotfinder.user.dto.UserResponse;
import com.spotfinder.user.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

  public UserResponse toResponse(UserEntity user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getRole(),
        user.getPrimaryActivity(),
        user.getEnabled(),
        user.getCreatedAt());
  }
}
