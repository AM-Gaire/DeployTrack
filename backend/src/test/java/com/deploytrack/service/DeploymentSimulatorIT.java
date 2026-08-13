package com.deploytrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.deploytrack.IntegrationTestBase;
import com.deploytrack.dto.CreateDeploymentRequest;
import com.deploytrack.dto.CreateProjectRequest;
import com.deploytrack.entity.Deployment;
import com.deploytrack.entity.DeploymentStatus;
import com.deploytrack.entity.Environment;
import com.deploytrack.entity.Project;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

// Every other integration test disables the simulator, so nothing ran the
// background path -- which is exactly how a change that broke it reached a
// running application. The simulator executes on its own thread with an empty
// security context, so any per-user check on the way to completing a
// deployment strands it at IN_PROGRESS forever.
//
// This test is the guard for that. It is slower than the rest because it waits
// on real asynchronous work, which is the price of covering the path at all.
@TestPropertySource(properties = {
    "deploytrack.simulator.enabled=true",
    "deploytrack.simulator.step-delay=50ms",
    // Deterministic: a random failure rate would make this flaky.
    "deploytrack.simulator.failure-rate=0",
})
class DeploymentSimulatorIT extends IntegrationTestBase {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private DeploymentSimulator simulator;

    @Autowired
    private com.deploytrack.repository.DeploymentRepository deploymentRepository;

    @Test
    @WithMockUser(username = "admin@test.local", roles = "ADMIN")
    void theSimulatorCompletesADeploymentWithoutASignedInUser() {
        Project project = projectService.create(
            new CreateProjectRequest("simulated-" + UUID.randomUUID(), "background completion"));
        Deployment deployment = deploymentService.trigger(project.getId(),
            new CreateDeploymentRequest("1.0.0", Environment.DEV));

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.IN_PROGRESS);

        // Runs on the async executor, with no security context of its own.
        simulator.simulate(deployment.getId());

        // Asserted against the repository rather than deploymentService.get,
        // because Awaitility polls on its own thread with no security context
        // -- the very condition this test exists to cover.
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            Deployment settled = deploymentRepository.findById(deployment.getId()).orElseThrow();
            assertThat(settled.getStatus())
                .as("the simulator must be able to complete a deployment on a thread with no user")
                .isEqualTo(DeploymentStatus.SUCCESS);
            assertThat(settled.getCompletedAt()).isNotNull();
        });
    }
}
