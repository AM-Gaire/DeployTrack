package com.deploytrack.controller;

import com.deploytrack.dto.CreateProjectRequest;
import com.deploytrack.dto.PagedResponse;
import com.deploytrack.dto.ProjectResponse;
import com.deploytrack.entity.Project;
import com.deploytrack.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Mirrors the /projects paths in docs/openapi.yaml exactly. Every endpoint
// requires authentication (see SecurityConfig's default-deny rule); the
// @PreAuthorize annotations below add the per-role restrictions from the
// permission matrix in docs/requirements.md. Reads are open to any logged-in
// role; writes need DEVELOPER or ADMIN.
//
// Role is only half the check. Whether the caller may touch *this particular*
// project is an ownership question the annotations cannot express, so
// ProjectService enforces that separately once the record is loaded.
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public PagedResponse<ProjectResponse> list(
        @RequestParam(required = false) String search,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        var result = projectService.list(search, PageRequest.of(page, size));
        // One batched lookup for the whole page rather than one per project.
        var latest = projectService.latestDeploymentsFor(result.getContent());
        boolean owners = projectService.canSeeOwners();
        return PagedResponse.from(result, p -> ProjectResponse.from(p, latest.get(p.getId()), owners));
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id) {
        return withLatestDeployment(projectService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request) {
        // A brand new project has no deployments, so its status is IDLE.
        return ProjectResponse.from(projectService.create(request), null, projectService.canSeeOwners());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody CreateProjectRequest request) {
        return withLatestDeployment(projectService.update(id, request));
    }

    // An existing project may already have deployments, so its status must be
    // resolved rather than assumed. Passing null here would report a live
    // project as IDLE.
    private ProjectResponse withLatestDeployment(Project project) {
        var latest = projectService.latestDeploymentsFor(List.of(project));
        return ProjectResponse.from(project, latest.get(project.getId()), projectService.canSeeOwners());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        projectService.delete(id);
    }
}
