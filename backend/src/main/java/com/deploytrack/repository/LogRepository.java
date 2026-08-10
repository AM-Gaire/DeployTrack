package com.deploytrack.repository;

import com.deploytrack.entity.LogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

// Deliberately minimal -- filtering by deployment/level with pagination
// arrives in Phase 6 alongside the logs and monitoring module.
public interface LogRepository extends JpaRepository<LogEntry, Long> {
}
