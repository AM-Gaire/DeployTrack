package com.deploytrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.deploytrack.dto.LogResponse;
import com.deploytrack.entity.DeploymentStatus;
import com.deploytrack.entity.LogLevel;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// The registry is the piece that leaks if it is wrong, so these tests are
// mostly about cleanup rather than delivery.
class LogStreamServiceTest {

    private LogStreamService logStreamService;

    @BeforeEach
    void setUp() {
        logStreamService = new LogStreamService();
    }

    private static LogResponse logLine(String message) {
        return new LogResponse(1L, 100L, LogLevel.INFO, message, Instant.now());
    }

    @Test
    void subscribingRegistersAnEmitter() {
        logStreamService.subscribe(100L, 30_000);

        assertThat(logStreamService.subscriberCount(100L)).isEqualTo(1);
        assertThat(logStreamService.activeStreamCount()).isEqualTo(1);
    }

    @Test
    void multipleSubscribersToTheSameDeploymentAreAllTracked() {
        logStreamService.subscribe(100L, 30_000);
        logStreamService.subscribe(100L, 30_000);
        logStreamService.subscribe(200L, 30_000);

        assertThat(logStreamService.subscriberCount(100L)).isEqualTo(2);
        assertThat(logStreamService.activeStreamCount()).isEqualTo(3);
    }

    @Test
    void completingAStreamDeregistersItsEmitters() {
        logStreamService.subscribe(100L, 30_000);
        logStreamService.subscribe(100L, 30_000);

        logStreamService.completeStream(100L, DeploymentStatus.SUCCESS.name());

        // The leak check: after completion nothing may remain referenced.
        assertThat(logStreamService.subscriberCount(100L)).isZero();
        assertThat(logStreamService.activeStreamCount()).isZero();
    }

    @Test
    void completingOneStreamLeavesOthersConnected() {
        logStreamService.subscribe(100L, 30_000);
        logStreamService.subscribe(200L, 30_000);

        logStreamService.completeStream(100L, DeploymentStatus.SUCCESS.name());

        assertThat(logStreamService.subscriberCount(200L)).isEqualTo(1);
    }

    @Test
    void publishingToADeploymentWithNoSubscribersIsHarmless() {
        // The common case once a deployment finishes and everyone disconnects.
        logStreamService.onLogEntryCreated(new LogEntryCreatedEvent(999L, logLine("nobody listening")));

        assertThat(logStreamService.activeStreamCount()).isZero();
    }

    @Test
    void completingAStreamTwiceIsHarmless() {
        logStreamService.subscribe(100L, 30_000);

        logStreamService.completeStream(100L, DeploymentStatus.SUCCESS.name());
        logStreamService.completeStream(100L, DeploymentStatus.SUCCESS.name());

        assertThat(logStreamService.activeStreamCount()).isZero();
    }

    @Test
    void deploymentCompletedEventClosesTheStream() {
        logStreamService.subscribe(100L, 30_000);

        logStreamService.onDeploymentCompleted(
            new DeploymentCompletedEvent(100L, DeploymentStatus.FAILED));

        assertThat(logStreamService.activeStreamCount()).isZero();
    }
}
