package com.chainlance.dto;

import com.chainlance.entity.User;

/** The only user details shown to other users: no email, bio or role. */
public record PublicUserResponse(Long id, String displayName) {

    public static PublicUserResponse from(User user) {
        return new PublicUserResponse(user.getId(), user.getDisplayName());
    }
}
