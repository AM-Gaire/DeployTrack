package com.deploytrack.service;

import com.deploytrack.dto.LogResponse;

// Carries a fully-formed DTO rather than the LogEntry entity. Listeners run
// after the transaction has committed and its persistence context is closed,
// so touching a lazy association on an entity there would throw
// LazyInitializationException.
public record LogEntryCreatedEvent(Long deploymentId, LogResponse log) {}
