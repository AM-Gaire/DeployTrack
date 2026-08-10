package com.deploytrack.repository;

import com.deploytrack.entity.LogEntry;
import com.deploytrack.entity.LogLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LogRepository extends JpaRepository<LogEntry, Long> {

    // Always paginated. Logs are the fastest-growing table in the system --
    // an unbounded fetch here would eventually load hundreds of thousands of
    // rows into memory to serve one request.
    @Query("""
        SELECT l FROM LogEntry l
        WHERE l.deployment.id = :deploymentId
          AND (:level IS NULL OR l.level = :level)
        """)
    Page<LogEntry> findFiltered(
        @Param("deploymentId") Long deploymentId,
        @Param("level") LogLevel level,
        Pageable pageable);
}
