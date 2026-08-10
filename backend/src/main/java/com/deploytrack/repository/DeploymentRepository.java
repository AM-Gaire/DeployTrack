package com.deploytrack.repository;

import com.deploytrack.entity.Deployment;
import com.deploytrack.entity.DeploymentStatus;
import com.deploytrack.entity.Environment;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    // Filters are optional: passing null for environment or status disables
    // that predicate. Doing it in one query with null-checks beats writing
    // four near-identical finder methods for every combination.
    @Query("""
        SELECT d FROM Deployment d
        WHERE d.project.id = :projectId
          AND (:environment IS NULL OR d.environment = :environment)
          AND (:status IS NULL OR d.status = :status)
        """)
    Page<Deployment> findFiltered(
        @Param("projectId") Long projectId,
        @Param("environment") Environment environment,
        @Param("status") DeploymentStatus status,
        Pageable pageable);

    // Guards against two deployments running against the same environment at
    // once, which would make the deployment history lie about what is
    // actually live.
    boolean existsByProjectIdAndEnvironmentAndStatus(
        Long projectId, Environment environment, DeploymentStatus status);

    // Fetches the newest deployment for MANY projects in a single query.
    //
    // The naive alternative -- looping over projects and querying each one's
    // latest deployment -- is the classic N+1 problem: 20 projects means 21
    // round trips. Here the subquery picks the max id per project and the
    // outer query returns exactly those rows, so listing any number of
    // projects costs one extra query total.
    //
    // Max id works as "most recent" because ids are monotonically increasing;
    // ordering by startedAt would need a tiebreak for identical timestamps.
    @Query("""
        SELECT d FROM Deployment d
        JOIN FETCH d.deployedBy
        JOIN FETCH d.project
        WHERE d.id IN (
            SELECT MAX(d2.id) FROM Deployment d2
            WHERE d2.project.id IN :projectIds
            GROUP BY d2.project.id
        )
        """)
    List<Deployment> findLatestForProjects(@Param("projectIds") Collection<Long> projectIds);

    // Counts every status in ONE query and returns only the aggregates.
    //
    // The obvious alternative -- findAll() then group in Java -- transfers
    // every deployment row over the wire and into heap just to produce four
    // numbers. It looks identical in behaviour and collapses at scale.
    // Databases exist to do this; let it.
    @Query("SELECT d.status, COUNT(d) FROM Deployment d GROUP BY d.status")
    List<Object[]> countByStatus();

    long countByStartedAtAfter(Instant since);

    // Average seconds between start and completion, computed database-side.
    // Only settled deployments have a completedAt, so IN_PROGRESS rows are
    // excluded rather than counted as zero-duration.
    @Query(value = """
        SELECT AVG(EXTRACT(EPOCH FROM (completed_at - started_at)))
        FROM deployments
        WHERE completed_at IS NOT NULL
        """, nativeQuery = true)
    Double findAverageDurationSeconds();

    @Query("""
        SELECT d FROM Deployment d
        JOIN FETCH d.deployedBy
        JOIN FETCH d.project
        ORDER BY d.startedAt DESC
        """)
    List<Deployment> findRecent(Pageable pageable);
}
