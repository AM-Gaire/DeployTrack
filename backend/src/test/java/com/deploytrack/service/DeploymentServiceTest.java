package com.deploytrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deploytrack.dto.CreateDeploymentRequest;
import com.deploytrack.entity.Deployment;
import com.deploytrack.entity.DeploymentStatus;
import com.deploytrack.entity.Environment;
import com.deploytrack.entity.LogEntry;
import com.deploytrack.entity.Project;
import com.deploytrack.entity.Role;
import com.deploytrack.entity.User;
import com.deploytrack.exception.InvalidStateTransitionException;
import com.deploytrack.exception.ResourceNotFoundException;
import com.deploytrack.repository.DeploymentRepository;
import com.deploytrack.repository.LogRepository;
import com.deploytrack.security.CurrentUserService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeploymentServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private LogRepository logRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DeploymentService deploymentService;

    @BeforeEach
    void stubLogPersistence() {
        // A real repository returns the persisted entity with its generated
        // id; an unstubbed mock returns null. appendLog builds an event from
        // that return value, so the mock has to behave like the real thing.
        when(logRepository.save(any(LogEntry.class))).thenAnswer(inv -> {
            LogEntry entry = inv.getArgument(0);
            entry.setId(1L);
            return entry;
        });
    }

    private static final User DEPLOYER =
        User.builder().id(1L).username("amrit").role(Role.DEVELOPER).build();

    private static Project project() {
        return Project.builder().id(1L).name("inventory-api").createdBy(DEPLOYER).build();
    }

    private static Deployment deploymentWith(DeploymentStatus status) {
        return Deployment.builder()
            .id(10L)
            .project(project())
            .version("2.4.1")
            .environment(Environment.PRODUCTION)
            .status(status)
            .deployedBy(DEPLOYER)
            .startedAt(Instant.now())
            .build();
    }

    @Test
    void triggerCreatesDeploymentInProgress() {
        when(projectService.get(1L)).thenReturn(project());
        when(currentUserService.require()).thenReturn(DEPLOYER);
        when(deploymentRepository.existsByProjectIdAndEnvironmentAndStatus(
            1L, Environment.PRODUCTION, DeploymentStatus.IN_PROGRESS)).thenReturn(false);
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> inv.getArgument(0));

        Deployment result = deploymentService.trigger(1L,
            new CreateDeploymentRequest("2.4.1", Environment.PRODUCTION));

        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.IN_PROGRESS);
        assertThat(result.getCompletedAt()).isNull();
        assertThat(result.getDeployedBy()).isEqualTo(DEPLOYER);
    }

    @Test
    void triggerWritesAStartingLogEntry() {
        when(projectService.get(1L)).thenReturn(project());
        when(currentUserService.require()).thenReturn(DEPLOYER);
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> inv.getArgument(0));

        deploymentService.trigger(1L, new CreateDeploymentRequest("2.4.1", Environment.PRODUCTION));

        verify(logRepository).save(any(LogEntry.class));
    }

    @Test
    void triggerRejectsConcurrentDeploymentToSameEnvironment() {
        when(projectService.get(1L)).thenReturn(project());
        when(deploymentRepository.existsByProjectIdAndEnvironmentAndStatus(
            1L, Environment.PRODUCTION, DeploymentStatus.IN_PROGRESS)).thenReturn(true);

        assertThatThrownBy(() -> deploymentService.trigger(1L,
            new CreateDeploymentRequest("2.4.2", Environment.PRODUCTION)))
            .isInstanceOf(InvalidStateTransitionException.class)
            .hasMessageContaining("already in progress");

        verify(deploymentRepository, never()).save(any());
    }

    @Test
    void triggerAllowsConcurrentDeploymentsToDifferentEnvironments() {
        // staging and production are independent; only the same environment
        // conflicts.
        when(projectService.get(1L)).thenReturn(project());
        when(currentUserService.require()).thenReturn(DEPLOYER);
        when(deploymentRepository.existsByProjectIdAndEnvironmentAndStatus(
            1L, Environment.STAGING, DeploymentStatus.IN_PROGRESS)).thenReturn(false);
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> inv.getArgument(0));

        Deployment result = deploymentService.trigger(1L,
            new CreateDeploymentRequest("2.4.1", Environment.STAGING));

        assertThat(result.getEnvironment()).isEqualTo(Environment.STAGING);
    }

    @Test
    void updateStatusCompletesAnInProgressDeployment() {
        when(deploymentRepository.findById(10L))
            .thenReturn(Optional.of(deploymentWith(DeploymentStatus.IN_PROGRESS)));

        Deployment result = deploymentService.updateStatus(10L, DeploymentStatus.SUCCESS);

        assertThat(result.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    void updateStatusRejectsChangingASettledDeployment() {
        // The retried-webhook case: a FAILED deployment must not become
        // SUCCESS just because a callback arrived twice.
        when(deploymentRepository.findById(10L))
            .thenReturn(Optional.of(deploymentWith(DeploymentStatus.FAILED)));

        assertThatThrownBy(() -> deploymentService.updateStatus(10L, DeploymentStatus.SUCCESS))
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void updateStatusRejectsReportingInProgress() {
        when(deploymentRepository.findById(10L))
            .thenReturn(Optional.of(deploymentWith(DeploymentStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> deploymentService.updateStatus(10L, DeploymentStatus.IN_PROGRESS))
            .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void getThrowsForUnknownDeployment() {
        when(deploymentRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deploymentService.get(404L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listValidatesProjectExistsFirst() {
        // An unknown project must 404 rather than returning an empty page,
        // which would wrongly imply the project exists with no deployments.
        when(projectService.get(404L)).thenThrow(new ResourceNotFoundException("Project 404 not found"));

        assertThatThrownBy(() -> deploymentService.list(404L, null, null, null))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
