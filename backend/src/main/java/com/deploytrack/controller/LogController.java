package com.deploytrack.controller;

import com.deploytrack.dto.LogResponse;
import com.deploytrack.dto.PagedResponse;
import com.deploytrack.entity.LogLevel;
import com.deploytrack.repository.LogRepository;
import com.deploytrack.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Read-only for now. Live streaming and the monitoring dashboard are Phase 6;
// this exists because deployments already write log entries and leaving them
// unreadable would be strange.
@RestController
@RequestMapping("/api/deployments/{deploymentId}/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogRepository logRepository;
    private final DeploymentService deploymentService;

    @GetMapping
    public PagedResponse<LogResponse> list(
        @PathVariable Long deploymentId,
        @RequestParam(required = false) LogLevel level,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        // 404 for an unknown deployment rather than an empty page.
        deploymentService.get(deploymentId);

        // Oldest first: logs are read as a narrative of what happened, unlike
        // the deployment list which is browsed newest-first.
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "timestamp", "id"));
        return PagedResponse.from(logRepository.findFiltered(deploymentId, level, pageable), LogResponse::from);
    }
}
