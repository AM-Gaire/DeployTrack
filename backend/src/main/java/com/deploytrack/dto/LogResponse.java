package com.deploytrack.dto;

import com.deploytrack.entity.LogEntry;
import com.deploytrack.entity.LogLevel;
import java.time.Instant;

public record LogResponse(Long id, Long deploymentId, LogLevel level, String message, Instant timestamp) {

    public static LogResponse from(LogEntry entry) {
        return new LogResponse(
            entry.getId(),
            entry.getDeployment().getId(),
            entry.getLevel(),
            entry.getMessage(),
            entry.getTimestamp()
        );
    }
}
