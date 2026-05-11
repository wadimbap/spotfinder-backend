package com.spotfinder.user.dto;

import com.spotfinder.user.entity.ActivityType;
import jakarta.validation.constraints.Size;

public record UpdateCurrentUserRequest(
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String displayName,
        ActivityType activityType
) {
}
