package com.spotfinder.auth.dto;

public record AuthResponse(
    String accessToken, String refreshToken, String tokenType, Long expiresIn) {}
