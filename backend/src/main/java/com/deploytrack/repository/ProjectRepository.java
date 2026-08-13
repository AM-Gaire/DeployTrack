package com.deploytrack.repository;

import com.deploytrack.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Project names are unique across the whole system, not per owner. Two
    // developers cannot both own an "api" project -- the name identifies a
    // deployable application, and duplicates would make deployment history
    // ambiguous to anyone reading it.
    boolean existsByNameIgnoreCase(String name);

    // One query serving both the scoped and unscoped cases. Passing null for
    // ownerId disables that predicate, which keeps the service from choosing
    // between four near-identical finder methods.
    //
    // search is an empty string rather than null when absent, and an empty
    // term matches everything through LIKE '%%'. A null string parameter has
    // no inferable type, so Postgres binds it as bytea and the query fails
    // with "function lower(bytea) does not exist" -- only on the unfiltered
    // path, which is the one the application uses most.
    @Query("""
        SELECT p FROM Project p
        WHERE (:ownerId IS NULL OR p.createdBy.id = :ownerId)
          AND LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
        """)
    Page<Project> findVisible(
        @Param("ownerId") Long ownerId,
        @Param("search") String search,
        Pageable pageable);

    long countByCreatedById(Long ownerId);
}
