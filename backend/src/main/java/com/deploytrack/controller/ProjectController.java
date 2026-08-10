package com.deploytrack.controller;

import com.deploytrack.dto.CreateProjectRequest;
import com.deploytrack.dto.PagedResponse;
import com.deploytrack.dto.ProjectResponse;
import com.deploytrack.service.ProjectService;
import jakarta.validation.Valid;
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
// role, writes need DEVELOPER or ADMIN, and deletion is ADMIN-only.
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
        return PagedResponse.from(result, ProjectResponse::from);
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable Long id) {
        return ProjectResponse.from(projectService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request) {
        return ProjectResponse.from(projectService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'ADMIN')")
    public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody CreateProjectRequest request) {
        return ProjectResponse.from(projectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        projectService.delete(id);
    }
}
