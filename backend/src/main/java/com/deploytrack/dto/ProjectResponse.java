package com.deploytrack.dto;

import com.deploytrack.entity.Deployment;
import com.deploytrack.entity.Project;
import java.time.Instant;

public record ProjectResponse(
    Long id,
    String name,
    String description,
    ProjectStatus status,
    UserSummary createdBy,
    DeploymentResponse latestDeployment,
    Instant createdAt
) {

    // Derived, never stored. A status column would be a second source of
    // truth that drifts out of sync with the deployment history the moment
    // any code path updates one without the other.
    public enum ProjectStatus {
        ACTIVE,
        DEPLOYING,
        FAILING,
        // No deployments yet -- distinct from ACTIVE, which claims something
        // is actually running.
        IDLE
    }

    public static ProjectResponse from(Project project, Deployment latest) {
        return new ProjectResponse(
            project.getId(),
            project.getName(),
            project.getDescription(),
            deriveStatus(latest),
            UserSummary.from(project.getCreatedBy()),
            latest == null ? null : DeploymentResponse.from(latest),
            project.getCreatedAt()
        );
    }

    private static ProjectStatus deriveStatus(Deployment latest) {
        if (latest == null) {
            return ProjectStatus.IDLE;
        }
        return switch (latest.getStatus()) {
            case IN_PROGRESS -> ProjectStatus.DEPLOYING;
            case SUCCESS -> ProjectStatus.ACTIVE;
            case FAILED -> ProjectStatus.FAILING;
        };
    }

}
