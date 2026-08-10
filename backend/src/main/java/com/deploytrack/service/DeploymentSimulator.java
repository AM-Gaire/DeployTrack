package com.deploytrack.service;

import com.deploytrack.entity.DeploymentStatus;
import com.deploytrack.entity.LogLevel;
import com.deploytrack.exception.InvalidStateTransitionException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// Stands in for a real CI/CD pipeline so the application demonstrates a full
// deployment lifecycle without external tooling. It drives the *same*
// DeploymentService.updateStatus method a real pipeline would call through
// PATCH /deployments/{id}/status -- it is another caller of the public API,
// not a parallel code path with its own rules.
//
// Disabled with deploytrack.simulator.enabled=false, which is how a real
// pipeline would take over. Integration tests disable it so that a background
// thread cannot change a deployment's status mid-assertion.
@Component
@ConditionalOnProperty(name = "deploytrack.simulator.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DeploymentSimulator {

    private static final Logger log = LoggerFactory.getLogger(DeploymentSimulator.class);

    private static final List<String> PROGRESS_MESSAGES = List.of(
        "Pulling container image",
        "Running database migrations",
        "Starting application instances",
        "Waiting for health checks to pass"
    );

    private final DeploymentService deploymentService;

    @Value("${deploytrack.simulator.step-delay:1s}")
    private Duration stepDelay;

    @Value("${deploytrack.simulator.failure-rate:0.2}")
    private double failureRate;

    @Async("deploymentExecutor")
    public void simulate(Long deploymentId) {
        try {
            for (String message : PROGRESS_MESSAGES) {
                Thread.sleep(stepDelay.toMillis());
                deploymentService.appendLogTo(deploymentId, LogLevel.INFO, message);
            }

            // Deployments fail in reality, and a dashboard that only ever
            // shows green proves nothing. A random minority fail so the
            // failure path is actually exercised.
            boolean failed = ThreadLocalRandom.current().nextDouble() < failureRate;
            if (failed) {
                deploymentService.appendLogTo(deploymentId, LogLevel.ERROR,
                    "Health check failed: application did not become ready in time");
            }
            deploymentService.updateStatus(deploymentId,
                failed ? DeploymentStatus.FAILED : DeploymentStatus.SUCCESS);

        } catch (InterruptedException ex) {
            // Restore the flag rather than swallowing it, so the pool's own
            // shutdown logic still sees the interruption.
            Thread.currentThread().interrupt();
            log.warn("Simulation of deployment {} was interrupted", deploymentId);
        } catch (InvalidStateTransitionException ex) {
            // Expected when a real pipeline (or a test) already reported the
            // outcome first. The state machine did its job; nothing to fix.
            log.debug("Deployment {} was already completed by another caller", deploymentId);
        } catch (Exception ex) {
            // An @Async method's exception vanishes unless it is caught here:
            // nobody is holding the Future, so an uncaught failure would
            // silently strand the deployment at IN_PROGRESS forever.
            log.error("Simulation of deployment {} failed unexpectedly", deploymentId, ex);
        }
    }
}
