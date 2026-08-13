package com.deploytrack.service;

import com.deploytrack.dto.DashboardStatsResponse;
import com.deploytrack.dto.DeploymentResponse;
import com.deploytrack.entity.DeploymentStatus;
import com.deploytrack.entity.Role;
import com.deploytrack.entity.User;
import com.deploytrack.repository.DeploymentRepository;
import com.deploytrack.repository.ProjectRepository;
import com.deploytrack.security.CurrentUserService;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int RECENT_DEPLOYMENT_LIMIT = 10;

    private final DeploymentRepository deploymentRepository;
    private final ProjectRepository projectRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public DashboardStatsResponse stats() {
        // Every figure below is scoped to what the caller can see, so a
        // developer's success rate describes their own work rather than the
        // whole system's. An unscoped dashboard beside a scoped project list
        // would be worse than either: percentages that cannot be reconciled
        // with anything on screen, and a rough signal of how much everyone
        // else is deploying.
        Long ownerId = visibilityScope();

        Map<DeploymentStatus, Long> byStatus = countsByStatus(ownerId);

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long succeeded = byStatus.getOrDefault(DeploymentStatus.SUCCESS, 0L);
        long failed = byStatus.getOrDefault(DeploymentStatus.FAILED, 0L);
        long settled = succeeded + failed;

        Instant now = Instant.now();

        return new DashboardStatsResponse(
            ownerId == null ? projectRepository.count() : projectRepository.countByCreatedById(ownerId),
            total,
            toResponseMap(byStatus),
            // Denominator is settled deployments, not all of them. Counting
            // IN_PROGRESS ones would drag the rate down every time a
            // deployment starts, making the number swing for no real reason.
            settled == 0 ? null : round(succeeded * 100.0 / settled),
            deploymentRepository.countSinceForOwner(now.minus(Duration.ofHours(24)), ownerId),
            deploymentRepository.countSinceForOwner(now.minus(Duration.ofDays(7)), ownerId),
            round(deploymentRepository.findAverageDurationSecondsForOwner(ownerId)),
            deploymentRepository.findRecentForOwner(ownerId, PageRequest.of(0, RECENT_DEPLOYMENT_LIMIT))
                .stream()
                .map(DeploymentResponse::from)
                .toList()
        );
    }

    // Mirrors ProjectService: DEVELOPER is scoped to their own work, ADMIN and
    // VIEWER see everything.
    private Long visibilityScope() {
        User caller = currentUserService.require();
        return caller.getRole() == Role.DEVELOPER ? caller.getId() : null;
    }

    private Map<DeploymentStatus, Long> countsByStatus(Long ownerId) {
        var counts = new EnumMap<DeploymentStatus, Long>(DeploymentStatus.class);
        for (Object[] row : deploymentRepository.countByStatusForOwner(ownerId)) {
            counts.put((DeploymentStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    private Map<String, Long> toResponseMap(Map<DeploymentStatus, Long> counts) {
        // LinkedHashMap so statuses always serialise in enum order -- a map
        // whose key order changes between requests makes the frontend's
        // rendering non-deterministic.
        var result = new LinkedHashMap<String, Long>();
        for (DeploymentStatus status : DeploymentStatus.values()) {
            result.put(status.name(), counts.getOrDefault(status, 0L));
        }
        return result;
    }

    // Raw division produces values like 66.66666666666667, which no dashboard
    // should ever display.
    private Double round(Double value) {
        return value == null ? null : Math.round(value * 10.0) / 10.0;
    }
}
