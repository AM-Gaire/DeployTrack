package com.deploytrack.service;

import com.deploytrack.dto.CreateDeploymentRequest;
import com.deploytrack.entity.Deployment;
import com.deploytrack.entity.DeploymentStatus;
import com.deploytrack.entity.Environment;
import com.deploytrack.entity.LogEntry;
import com.deploytrack.entity.LogLevel;
import com.deploytrack.entity.Project;
import com.deploytrack.entity.User;
import com.deploytrack.exception.InvalidStateTransitionException;
import com.deploytrack.exception.ResourceNotFoundException;
import com.deploytrack.repository.DeploymentRepository;
import com.deploytrack.repository.LogRepository;
import com.deploytrack.security.CurrentUserService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final LogRepository logRepository;
    private final ProjectService projectService;
    private final CurrentUserService currentUserService;

    public Page<Deployment> list(Long projectId, Environment environment, DeploymentStatus status,
                                 Pageable pageable) {
        // Load the project first so an unknown id gives 404 rather than an
        // empty page, which would wrongly imply the project exists but has
        // no deployments.
        projectService.get(projectId);
        return deploymentRepository.findFiltered(projectId, environment, status, pageable);
    }

    public Deployment get(Long id) {
        return deploymentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Deployment " + id + " not found"));
    }

    // Returns as soon as the record exists; the deployment itself is still
    // running. That is why the controller answers 202 Accepted rather than
    // 201 Created -- the resource exists, the work does not yet.
    @Transactional
    public Deployment trigger(Long projectId, CreateDeploymentRequest request) {
        Project project = projectService.get(projectId);
        User deployer = currentUserService.require();

        // Two deployments running against one environment at the same time
        // means the history can no longer say what is actually live. Real
        // systems queue or reject; rejecting is the honest simple option.
        if (deploymentRepository.existsByProjectIdAndEnvironmentAndStatus(
            projectId, request.environment(), DeploymentStatus.IN_PROGRESS)) {
            throw new InvalidStateTransitionException(
                "A deployment to " + request.environment() + " is already in progress for this project");
        }

        Deployment deployment = deploymentRepository.save(Deployment.builder()
            .project(project)
            .version(request.version())
            .environment(request.environment())
            .status(DeploymentStatus.IN_PROGRESS)
            .deployedBy(deployer)
            .startedAt(Instant.now())
            .build());

        appendLog(deployment, LogLevel.INFO,
            "Deployment of version " + deployment.getVersion()
                + " to " + deployment.getEnvironment() + " started");

        return deployment;
    }

    // The integration point a CI pipeline calls when a deployment finishes.
    // The simulator uses the same method, so there is exactly one code path
    // that can complete a deployment.
    @Transactional
    public Deployment updateStatus(Long deploymentId, DeploymentStatus target) {
        Deployment deployment = get(deploymentId);

        if (!deployment.getStatus().canTransitionTo(target)) {
            // Covers both directions of illegality: reporting a non-terminal
            // status, and re-reporting one that already settled. A retried
            // webhook must not be able to flip a FAILED deployment to SUCCESS.
            throw new InvalidStateTransitionException(
                "Cannot change deployment status from " + deployment.getStatus() + " to " + target);
        }

        deployment.setStatus(target);
        deployment.setCompletedAt(Instant.now());

        appendLog(deployment,
            target == DeploymentStatus.SUCCESS ? LogLevel.INFO : LogLevel.ERROR,
            "Deployment finished with status " + target);

        return deployment;
    }

    private void appendLog(Deployment deployment, LogLevel level, String message) {
        logRepository.save(LogEntry.builder()
            .deployment(deployment)
            .level(level)
            .message(message)
            .timestamp(Instant.now())
            .build());
    }

    // Id-based variant for callers outside the original transaction -- notably
    // the async simulator, which runs on its own thread. Passing the entity
    // across a thread boundary would hand it a detached instance whose
    // persistence context has long since closed.
    @Transactional
    public void appendLogTo(Long deploymentId, LogLevel level, String message) {
        appendLog(get(deploymentId), level, message);
    }
}
