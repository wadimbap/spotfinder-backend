package com.spotfinder.auth.security;

import com.spotfinder.user.entity.UserRole;
import java.util.UUID;

public record UserPrincipal(
        UUID id,
        String email,
        UserRole role
) {
}
