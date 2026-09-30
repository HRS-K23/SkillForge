package com.project.skillforge.auth;

import com.project.skillforge.users.UserResponse;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {}
