package com.project.skillforge.users;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, String role, Instant createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.getCreatedAt());
    }
}

