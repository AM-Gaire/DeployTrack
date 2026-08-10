package com.deploytrack.service;

import com.deploytrack.dto.CreateProjectRequest;
import com.deploytrack.entity.Project;
import com.deploytrack.entity.User;
import com.deploytrack.exception.DuplicateResourceException;
import com.deploytrack.exception.ResourceNotFoundException;
import com.deploytrack.repository.ProjectRepository;
import com.deploytrack.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
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
        project.setName(request.name());
        project.setDescription(request.description());
        return project;
    }

    @Transactional
    public void delete(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new ResourceNotFoundException("Project " + id + " not found");
        }
        projectRepository.deleteById(id);
    }
}
