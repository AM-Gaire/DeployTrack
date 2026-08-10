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

    public Page<Project> list(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return projectRepository.findByNameContainingIgnoreCase(search, pageable);
        }
        return projectRepository.findAll(pageable);
    }

    public Project get(Long id) {
        return projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project " + id + " not found"));
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
