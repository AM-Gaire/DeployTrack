package com.deploytrack.dto;

import java.util.List;
import java.util.Map;

public record DashboardStatsResponse(
    long totalProjects,
    long totalDeployments,
    // Every status appears, including ones with zero occurrences. Omitting
    // empty buckets forces the frontend to guess whether a missing key means
    // "none" or "the backend forgot", and makes charts jump around as
    // categories appear and disappear.
    Map<String, Long> deploymentsByStatus,
    // Null rather than 0.0 when nothing has settled yet: a success rate of
    // zero would wrongly read as "everything is failing".
    Double successRatePercent,
    long deploymentsLast24Hours,
    long deploymentsLast7Days,
    Double averageDurationSeconds,
    List<DeploymentResponse> recentDeployments
) {}
