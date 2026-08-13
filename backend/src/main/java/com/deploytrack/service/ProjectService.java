package com.deploytrack.service;

import com.deploytrack.dto.CreateProjectRequest;
import com.deploytrack.entity.Deployment;
import com.deploytrack.entity.Project;
import com.deploytrack.entity.Role;
import com.deploytrack.entity.User;
import com.deploytrack.exception.DuplicateResourceException;
import com.deploytrack.exception.ResourceNotFoundException;
import com.deploytrack.repository.DeploymentRepository;
import com.deploytrack.repository.ProjectRepository;
import com.deploytrack.security.CurrentUserService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final DeploymentRepository deploymentRepository;
    private final CurrentUserService currentUserService;

    // Which projects a caller may see at all.
    //
    // ADMIN and VIEWER see everything: an admin manages the system, and a
    // viewer exists to observe it -- a read-only role that can only see its
    // own work would have nothing to look at.
    //
    // A DEVELOPER sees only what they created. Returning null means "no owner
    // filter", which the repository query treats as unscoped.
    private Long visibilityScope() {
        User caller = currentUserService.require();
        return caller.getRole() == Role.DEVELOPER ? caller.getId() : null;
    }

    public Page<Project> list(String search, Pageable pageable) {
        // Empty rather than null: an empty term matches every name, and a null
        // string parameter has no type Postgres can infer.
        String term = (search == null || search.isBlank()) ? "" : search.trim();
        return projectRepository.findVisible(visibilityScope(), term, pageable);
    }

    public Project get(Long id) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project " + id + " not found"));

        // Scoping the list alone would be presentation, not enforcement -- a
        // developer could still open someone else's project by typing its id
        // into the URL. This is the same class of hole as the IDOR fixed on
        // the write path.
        //
        // 404 rather than 403 on purpose: a 403 would confirm the project
        // exists, which is exactly what a caller who cannot see it should not
        // learn. To them it is indistinguishable from an id that was never
        // used.
        Long scope = visibilityScope();
        if (scope != null && !project.getCreatedBy().getId().equals(scope)) {
            throw new ResourceNotFoundException("Project " + id + " not found");
        }

        return project;
    }

    // True when the caller is allowed to know who owns a project. Only an
    // admin needs it: a developer sees nothing but their own work, so the
    // name would be their own on every row, and a viewer has no reason to
    // know who owns what.
    public boolean canSeeOwners() {
        return currentUserService.require().getRole() == Role.ADMIN;
    }

    // Resolves the newest deployment for a batch of projects in ONE query.
    //
    // The obvious implementation -- asking for each project's latest
    // deployment inside the mapping loop -- is the N+1 problem: one query for
    // the page of projects, then one more per project. It looks fine against
    // three rows in development and falls over at scale, which is exactly why
    // it is so common. Fetching the whole batch up front keeps a page of any
    // size at two queries total.
    //
    // Projects with no deployments are simply absent from the map, and
    // ProjectResponse maps that absence to IDLE.
    @Transactional(readOnly = true)
    public Map<Long, Deployment> latestDeploymentsFor(Collection<Project> projects) {
        if (projects.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = projects.stream().map(Project::getId).toList();
        return deploymentRepository.findLatestForProjects(ids).stream()
            .collect(Collectors.toMap(d -> d.getProject().getId(), Function.identity()));
    }

    @Transactional
    public Project create(CreateProjectRequest request) {
        if (projectRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A project named '" + request.name() + "' already exists");
        }

        User creator = currentUserService.require();

        Project project = Project.builder()
            .name(request.name())
            .description(request.description())
            .createdBy(creator)
            .build();
        return projectRepository.save(project);
    }

    @Transactional
    public Project update(Long id, CreateProjectRequest request) {
        Project project = get(id);
        requireCanModify(project);

        // Renaming to a name another project already holds must be rejected,
        // but renaming a project to its own current name is a no-op and must
        // stay legal -- otherwise editing only the description would fail.
        if (!project.getName().equalsIgnoreCase(request.name())
            && projectRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A project named '" + request.name() + "' already exists");
        }

        project.setName(request.name());
        project.setDescription(request.description());
        return project;
    }

    @Transactional
    public void delete(Long id) {
        Project project = get(id);
        requireCanModify(project);
        projectRepository.delete(project);
    }

    // Ownership check, distinct from the role check on the controller. Roles
    // answer "what kind of user are you"; this answers "is this yours". Only
    // enforcing the former is how IDOR bugs happen -- a valid DEVELOPER token
    // plus someone else's project id would otherwise be enough to edit it.
    private void requireCanModify(Project project) {
        User caller = currentUserService.require();

        if (caller.getRole() == Role.ADMIN) {
            return;
        }
        // Compare ids rather than entity instances: createdBy is a lazy proxy
        // here, so equals() between it and a separately loaded User is not
        // reliable.
        if (!project.getCreatedBy().getId().equals(caller.getId())) {
            throw new AccessDeniedException("You can only modify projects you created");
        }
    }
}
