package com.deploytrack.dto;

import jakarta.validation.constraints.NotBlank;

// Deliberately no @Size or @Email constraints on the password here: login
// should not reveal what a valid password looks like, and a legacy user
// whose password predates a rule change must still be able to log in.
public record LoginRequest(

    @NotBlank(message = "email is required")
    String email,

    @NotBlank(message = "password is required")
    String password
) {}
