package com.deploytrack.controller;

import com.deploytrack.dto.CreateDeploymentRequest;
import com.deploytrack.dto.DeploymentResponse;
import com.deploytrack.dto.PagedResponse;
import com.deploytrack.dto.UpdateDeploymentStatusRequest;
import com.deploytrack.entity.Deployment;
import com.deploytrack.entity.DeploymentStatus;
import com.deploytrack.entity.Environment;
import com.deploytrack.service.DeploymentService;
import com.deploytrack.service.DeploymentSimulator;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DeploymentController {

    private final DeploymentService deploymentService;
    // ObjectProvider because the simulator is conditional -- when it is
    // switched off the bean does not exist, and a plain constructor
    // dependency would fail startup.
    private final ObjectProvider<DeploymentSimulator> simulator;

    public DeploymentController(DeploymentService deploymentService,
                                ObjectProvider<DeploymentSimulator> simulator) {
        this.deploymentService = deploymentService;
        this.simulator = simulator;
    }

    @GetMapping("/projects/{projectId}/deployments")
    public PagedResponse<DeploymentResponse> list(
        @PathVariable Long projectId,
        @RequestParam(required = false) Environment environment,
        @RequestParam(required = false) DeploymentStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        // Newest first: a deployment history is read from the top, and the
        // frontend's timeline depends on this order rather than re-sorting.
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = deploymentService.list(projectId, environment, status, pageable);
        return PagedResponse.from(result, DeploymentResponse::from);
    }

    // 202 Accepted, not 201 Created. The deployment record exists, but the
    // work it represents has not finished -- the client must poll (or, from
    // Phase 6, subscribe) to learn the outcome.
    @PostMapping("/projects/{projectId}/deployments")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
    public ResponseEntity<DeploymentResponse> trigger(
        @PathVariable Long projectId,
        @Valid @RequestBody CreateDeploymentRequest request
    ) {
        Deployment deployment = deploymentService.trigger(projectId, request);

        // Kick off simulated progress only after the record is committed, so
        // the async thread cannot look up an id that is not yet visible to it.
        simulator.ifAvailable(s -> s.simulate(deployment.getId()));

        return ResponseEntity.accepted().body(DeploymentResponse.from(deployment));
    }

    @GetMapping("/deployments/{deploymentId}")
    public DeploymentResponse get(@PathVariable Long deploymentId) {
        return DeploymentResponse.from(deploymentService.get(deploymentId));
    }

    // The callback a CI/CD pipeline uses to report an outcome. PATCH rather
    // than PUT because it changes one field, not the whole resource.
    @PatchMapping("/deployments/{deploymentId}/status")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
    public DeploymentResponse updateStatus(
        @PathVariable Long deploymentId,
        @Valid @RequestBody UpdateDeploymentStatusRequest request
    ) {
        return DeploymentResponse.from(deploymentService.updateStatus(deploymentId, request.status()));
    }
}
