package com.deploytrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.security.access.AccessDeniedException;

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

    private static User user(Long id, Role role) {
        return User.builder().id(id).username("u" + id).role(role).build();
    }

    private static Project projectOwnedBy(User owner) {
        return Project.builder().id(1L).name("inventory-api").description("Stock").createdBy(owner).build();
    }

    @Test
    void updateAllowsTheProjectOwner() {
        User owner = user(1L, Role.DEVELOPER);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(projectOwnedBy(owner)));
        when(currentUserService.require()).thenReturn(owner);

        Project updated = projectService.update(1L, new CreateProjectRequest("inventory-api", "New description"));

        assertThat(updated.getDescription()).isEqualTo("New description");
    }

    @Test
    void updateHidesAnotherDevelopersProject() {
        // The IDOR case: a legitimately authenticated DEVELOPER passes the
        // role check, then supplies someone else's project id.
        //
        // Not-found rather than forbidden, because a developer cannot see
        // other people's projects at all now. A 403 would confirm the project
        // exists, which is exactly what someone probing ids should not learn.
        User owner = user(1L, Role.DEVELOPER);
        User otherDeveloper = user(2L, Role.DEVELOPER);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(projectOwnedBy(owner)));
        when(currentUserService.require()).thenReturn(otherDeveloper);

        assertThatThrownBy(() -> projectService.update(1L, new CreateProjectRequest("hijacked", "mine now")))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateAllowsAdminOnAnyProject() {
        User owner = user(1L, Role.DEVELOPER);
        User admin = user(99L, Role.ADMIN);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(projectOwnedBy(owner)));
        when(currentUserService.require()).thenReturn(admin);

        Project updated = projectService.update(1L, new CreateProjectRequest("inventory-api", "Admin edited"));

        assertThat(updated.getDescription()).isEqualTo("Admin edited");
    }

    @Test
    void updateAllowsKeepingTheSameName() {
        // Editing only the description must not trip the duplicate-name check
        // against the project's own existing name.
        User owner = user(1L, Role.DEVELOPER);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(projectOwnedBy(owner)));
        when(currentUserService.require()).thenReturn(owner);

        Project updated = projectService.update(1L, new CreateProjectRequest("inventory-api", "Only desc changed"));

        assertThat(updated.getName()).isEqualTo("inventory-api");
    }

    @Test
    void updateRejectsRenameOntoAnExistingName() {
        User owner = user(1L, Role.DEVELOPER);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(projectOwnedBy(owner)));
        when(currentUserService.require()).thenReturn(owner);
        when(projectRepository.existsByNameIgnoreCase("billing-service")).thenReturn(true);

        assertThatThrownBy(() -> projectService.update(1L, new CreateProjectRequest("billing-service", "x")))
            .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deleteHidesAnotherDevelopersProject() {
        User owner = user(1L, Role.DEVELOPER);
        User otherDeveloper = user(2L, Role.DEVELOPER);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(projectOwnedBy(owner)));
        when(currentUserService.require()).thenReturn(otherDeveloper);

        assertThatThrownBy(() -> projectService.delete(1L))
            .isInstanceOf(ResourceNotFoundException.class);

        // What matters most: nothing was deleted.
        verify(projectRepository, never()).delete(any(Project.class));
    }

    @Test
    void deleteAllowsTheOwner() {
        User owner = user(1L, Role.DEVELOPER);
        Project project = projectOwnedBy(owner);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(currentUserService.require()).thenReturn(owner);

        projectService.delete(1L);

        verify(projectRepository).delete(project);
    }

    @Test
    void deleteAllowsAdminOnAnyProject() {
        User owner = user(1L, Role.DEVELOPER);
        Project project = projectOwnedBy(owner);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(currentUserService.require()).thenReturn(user(99L, Role.ADMIN));

        projectService.delete(1L);

        verify(projectRepository).delete(project);
    }

    @Test
    void deleteThrowsNotFoundForMissingProject() {
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.delete(404L))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
