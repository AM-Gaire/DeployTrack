package com.deploytrack.exception;

// Distinct from a validation error: the payload is well-formed and the caller
// is authorised, but the action is illegal given the resource's current state.
// Maps to 409 Conflict, the status for "your request conflicts with the state
// of the thing you are acting on".
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
