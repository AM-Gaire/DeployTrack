package com.deploytrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be 100 characters or fewer")
    String name,

    @Size(max = 500, message = "description must be 500 characters or fewer")
    String description
) {}
