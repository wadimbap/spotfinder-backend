package com.spotfinder.user.dto;

import com.spotfinder.user.entity.ActivityType;
import com.spotfinder.user.entity.UserRole;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record UserResponse(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        ActivityType primaryActivity,
        Boolean enabled,
        Instant createdAt) {
}
