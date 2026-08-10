package com.deploytrack.repository;

import com.deploytrack.entity.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;

// Deliberately minimal -- query methods for filtering by project/environment/
// status and finding the latest deployment per project arrive in Phase 5
// alongside the deployment tracking module, once there's a real use for them.
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
}
