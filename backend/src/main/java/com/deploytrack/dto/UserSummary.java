package com.deploytrack.dto;

import com.deploytrack.entity.Role;
import com.deploytrack.entity.User;

public record UserSummary(Long id, String username, String email, Role role) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
