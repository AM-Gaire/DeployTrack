package com.deploytrack.dto;

import com.deploytrack.entity.DeploymentStatus;
import jakarta.validation.constraints.NotNull;

// The payload a CI pipeline sends back when a deployment finishes. Only
// terminal statuses are meaningful here -- reporting IN_PROGRESS would be a
// no-op, and DeploymentService rejects it rather than silently ignoring it.
public record UpdateDeploymentStatusRequest(

    @NotNull(message = "status is required and must be one of SUCCESS, FAILED")
    DeploymentStatus status
) {}
