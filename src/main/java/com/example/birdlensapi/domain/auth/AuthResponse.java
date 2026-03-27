package com.example.birdlensapi.domain.auth;

import com.example.birdlensapi.domain.user.UserResponse;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserResponse user
) {}