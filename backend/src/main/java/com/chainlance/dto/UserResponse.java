package com.chainlance.dto;

import com.chainlance.entity.User;
import com.chainlance.entity.enums.Role;
import java.time.Instant;

/** Public view of a user. Never includes the password hash. */
public record UserResponse(Long id, String email, String displayName, Role role, String bio, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.getBio(), user.getCreatedAt());
    }
}
