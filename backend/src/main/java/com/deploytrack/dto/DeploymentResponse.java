package com.deploytrack.dto;

import com.deploytrack.entity.Deployment;
import com.deploytrack.entity.DeploymentStatus;
import com.deploytrack.entity.Environment;
import java.time.Instant;

public record DeploymentResponse(
    Long id,
    Long projectId,
    String version,
    Environment environment,
    DeploymentStatus status,
    UserSummary deployedBy,
    Instant startedAt,
    Instant completedAt
) {

    public static DeploymentResponse from(Deployment deployment) {
        return new DeploymentResponse(
            deployment.getId(),
            deployment.getProject().getId(),
            deployment.getVersion(),
            deployment.getEnvironment(),
            deployment.getStatus(),
            UserSummary.from(deployment.getDeployedBy()),
            deployment.getStartedAt(),
            deployment.getCompletedAt()
        );
    }
}
