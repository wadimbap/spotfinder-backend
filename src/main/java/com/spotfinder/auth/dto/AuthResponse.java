package com.spotfinder.auth.dto;

import com.spotfinder.user.dto.UserResponse;

public record AuthResponse(
        String accessToken,
        String tokenType,
        UserResponse user
) {
}
