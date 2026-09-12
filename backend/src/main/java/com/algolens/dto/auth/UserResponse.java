package com.algolens.dto.auth;

import com.algolens.entity.User;
import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        String role,
        int creditBalance,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), user.getCreditBalance(), user.getCreatedAt());
    }
}
