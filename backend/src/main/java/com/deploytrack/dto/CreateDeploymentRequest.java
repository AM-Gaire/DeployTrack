package com.deploytrack.dto;

import com.deploytrack.entity.Environment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Status is absent on purpose: a caller triggers a deployment, it does not
// get to declare the outcome. Every new deployment starts IN_PROGRESS.
public record CreateDeploymentRequest(

    @NotBlank(message = "version is required")
    @Size(max = 50, message = "version must be 50 characters or fewer")
    String version,

    @NotNull(message = "environment is required and must be one of dev, staging, production")
    Environment environment
) {}
