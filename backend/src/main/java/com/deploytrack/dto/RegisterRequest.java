package com.deploytrack.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Note there is no "role" field: a client must never be able to choose its
// own role, or anyone could register themselves as ADMIN. Role is assigned
// server-side in AuthService (always DEVELOPER for self-registration).
public record RegisterRequest(

    @NotBlank(message = "username is required")
    @Size(min = 3, max = 50, message = "username must be between 3 and 50 characters")
    String username,

    @NotBlank(message = "email is required")
    @Email(message = "must be a valid email address")
    String email,

    @NotBlank(message = "password is required")
    @Size(min = 8, message = "password must be at least 8 characters")
    String password
) {}
