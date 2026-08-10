package com.deploytrack.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// The state machine is the rule that keeps deployment history truthful, so
// every transition -- legal and illegal -- is asserted explicitly rather than
// trusted to the implementation.
class DeploymentStatusTest {

    @Test
    void inProgressCanReachBothTerminalStates() {
        assertThat(DeploymentStatus.IN_PROGRESS.canTransitionTo(DeploymentStatus.SUCCESS)).isTrue();
        assertThat(DeploymentStatus.IN_PROGRESS.canTransitionTo(DeploymentStatus.FAILED)).isTrue();
    }

    @Test
    void inProgressCannotTransitionToItself() {
        assertThat(DeploymentStatus.IN_PROGRESS.canTransitionTo(DeploymentStatus.IN_PROGRESS)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = DeploymentStatus.class, names = {"SUCCESS", "FAILED"})
    void terminalStatesAreFinal(DeploymentStatus terminal) {
        // The property that matters most: a duplicated or retried CI callback
        // must never be able to rewrite a settled outcome.
        for (DeploymentStatus target : DeploymentStatus.values()) {
            assertThat(terminal.canTransitionTo(target))
                .as("%s should not transition to %s", terminal, target)
                .isFalse();
        }
    }

    @Test
    void onlyInProgressIsNonTerminal() {
        assertThat(DeploymentStatus.IN_PROGRESS.isTerminal()).isFalse();
        assertThat(DeploymentStatus.SUCCESS.isTerminal()).isTrue();
        assertThat(DeploymentStatus.FAILED.isTerminal()).isTrue();
    }
}
