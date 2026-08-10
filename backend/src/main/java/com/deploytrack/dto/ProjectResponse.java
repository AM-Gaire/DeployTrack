package com.deploytrack.dto;

import com.deploytrack.entity.Project;
import java.time.Instant;

// No "status" or "latestDeployment" field yet -- those depend on the
// Deployment feature, which doesn't exist until Phase 5. Adding them now
// would mean shipping fields that are always null, which is worse than
// adding them later when there's a real value behind them.
public record ProjectResponse(
    Long id,
    String name,
    String description,
    UserSummary createdBy,
    Instant createdAt
) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
            project.getId(),
            project.getName(),
            project.getDescription(),
            UserSummary.from(project.getCreatedBy()),
            project.getCreatedAt()
        );
    }
}
