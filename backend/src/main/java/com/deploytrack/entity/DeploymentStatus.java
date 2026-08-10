package com.deploytrack.entity;

// A deployment's lifecycle is a state machine, not a free-form field.
// IN_PROGRESS is the only non-terminal state; SUCCESS and FAILED are final.
// Encoding the rules here rather than scattering if-statements through the
// service means an illegal transition is impossible to express by accident,
// and a retried or duplicated CI callback cannot rewrite settled history.
public enum DeploymentStatus {
    IN_PROGRESS,
    SUCCESS,
    FAILED;

    public boolean isTerminal() {
        return this != IN_PROGRESS;
    }

    public boolean canTransitionTo(DeploymentStatus target) {
        return this == IN_PROGRESS && target.isTerminal();
    }
}
