package com.deploytrack.repository;

import com.deploytrack.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByNameIgnoreCase(String name);

    Page<Project> findByNameContainingIgnoreCase(String search, Pageable pageable);
}
