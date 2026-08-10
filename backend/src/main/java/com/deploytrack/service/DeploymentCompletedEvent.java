package com.deploytrack.service;

import com.deploytrack.entity.DeploymentStatus;

public record DeploymentCompletedEvent(Long deploymentId, DeploymentStatus status) {}
