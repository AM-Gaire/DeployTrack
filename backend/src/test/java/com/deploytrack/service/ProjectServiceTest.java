package com.deploytrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.deploytrack.dto.CreateProjectRequest;
import com.deploytrack.entity.Project;
import com.deploytrack.entity.Role;
import com.deploytrack.entity.User;
import com.deploytrack.exception.DuplicateResourceException;
import com.deploytrack.exception.ResourceNotFoundException;
import com.deploytrack.repository.ProjectRepository;
import com.deploytrack.security.CurrentUserService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// Pure unit tests: the repositories are mocked, so this verifies
// ProjectService's own decision-making (duplicate check, not-found check)
// without needing a running Postgres instance.
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void createRejectsDuplicateName() {
        when(projectRepository.existsByNameIgnoreCase("inventory-api")).thenReturn(true);

        var request = new CreateProjectRequest("inventory-api", "Stock levels");

        assertThatThrownBy(() -> projectService.create(request))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("inventory-api");
    }

    @Test
    void createAttributesProjectToAuthenticatedUser() {
        when(projectRepository.existsByNameIgnoreCase("billing-service")).thenReturn(false);
        User caller = User.builder().id(7L).username("amrit").role(Role.DEVELOPER).build();
        when(currentUserService.require()).thenReturn(caller);
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new CreateProjectRequest("billing-service", "Invoices and webhooks");
        Project created = projectService.create(request);

        assertThat(created.getName()).isEqualTo("billing-service");
        assertThat(created.getCreatedBy()).isEqualTo(caller);
    }

    @Test
    void getThrowsWhenProjectMissing() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.get(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
