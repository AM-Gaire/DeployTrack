package com.deploytrack.config;

import com.deploytrack.entity.Deployment;
import com.deploytrack.entity.DeploymentStatus;
import com.deploytrack.entity.Environment;
import com.deploytrack.entity.LogEntry;
import com.deploytrack.entity.LogLevel;
import com.deploytrack.entity.Project;
import com.deploytrack.entity.Role;
import com.deploytrack.entity.User;
import com.deploytrack.repository.DeploymentRepository;
import com.deploytrack.repository.LogRepository;
import com.deploytrack.repository.ProjectRepository;
import com.deploytrack.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Creates a read-only VIEWER account with realistic history so anyone visiting
// the deployed app can look around without an account. There is no marketing
// page; this is how a stranger gets through the front door.
//
// A VIEWER rather than a DEVELOPER on purpose. Visitors cannot mutate shared
// demo data, and the permission model demonstrates itself -- every create and
// edit control is correctly absent from the first screen onward.
//
// Off by default. It must never populate a real deployment with fake projects,
// so it only runs where explicitly enabled.
@Component
@ConditionalOnProperty(name = "deploytrack.demo.enabled", havingValue = "true")
// Runs after AdminBootstrap so the two seeders cannot race for the same
// username or email.
@Order(20)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final DeploymentRepository deploymentRepository;
    private final LogRepository logRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoEmail;
    private final String demoPassword;

    public DemoDataSeeder(
        UserRepository userRepository,
        ProjectRepository projectRepository,
        DeploymentRepository deploymentRepository,
        LogRepository logRepository,
        PasswordEncoder passwordEncoder,
        @Value("${deploytrack.demo.email:demo@deploytrack.dev}") String demoEmail,
        @Value("${deploytrack.demo.password:}") String demoPassword
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.deploymentRepository = deploymentRepository;
        this.logRepository = logRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoEmail = demoEmail;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Idempotent: this runs on every startup, so it must be safe to repeat.
        // Once the demo user exists we never touch it or its data again --
        // otherwise a restart would wipe whatever a visitor was looking at.
        if (userRepository.existsByEmail(demoEmail)) {
            return;
        }
        if (demoPassword.isBlank()) {
            log.warn("Demo mode is enabled but deploytrack.demo.password is not set. Skipping demo seed.");
            return;
        }

        User demoUser = userRepository.save(User.builder()
            .username("demo")
            .email(demoEmail)
            .passwordHash(passwordEncoder.encode(demoPassword))
            .role(Role.VIEWER)
            .build());

        // An author for the demo content. Projects require a creator, and
        // attributing them to the viewer would imply they could edit them.
        User author = userRepository.findFirstByRole(Role.ADMIN)
            .orElse(demoUser);

        Instant now = Instant.now();

        seedProject(author, "inventory-api", "Stock levels across warehouses", List.of(
            new Seed("2.4.1", Environment.PRODUCTION, DeploymentStatus.SUCCESS, now.minus(Duration.ofHours(2))),
            new Seed("2.4.0", Environment.STAGING, DeploymentStatus.SUCCESS, now.minus(Duration.ofDays(1))),
            new Seed("2.3.9", Environment.PRODUCTION, DeploymentStatus.FAILED, now.minus(Duration.ofDays(3)))
        ));

        seedProject(author, "billing-service", "Invoices and payment webhooks", List.of(
            new Seed("1.9.0", Environment.PRODUCTION, DeploymentStatus.FAILED, now.minus(Duration.ofMinutes(40))),
            new Seed("1.8.4", Environment.PRODUCTION, DeploymentStatus.SUCCESS, now.minus(Duration.ofDays(2)))
        ));

        seedProject(author, "auth-gateway", "Central JWT issuer and validator", List.of(
            new Seed("3.0.0-rc1", Environment.STAGING, DeploymentStatus.SUCCESS, now.minus(Duration.ofHours(6))),
            new Seed("2.9.2", Environment.PRODUCTION, DeploymentStatus.SUCCESS, now.minus(Duration.ofDays(5)))
        ));

        // Deliberately left with no deployments so the IDLE state and the
        // "no deployments yet" empty state are both visible in the demo.
        projectRepository.save(Project.builder()
            .name("reporting-worker")
            .description("Nightly report generation")
            .createdBy(author)
            .build());

        log.info("Seeded demo workspace for '{}'", demoEmail);
    }

    private record Seed(String version, Environment environment, DeploymentStatus status, Instant startedAt) {}

    private void seedProject(User author, String name, String description, List<Seed> seeds) {
        Project project = projectRepository.save(Project.builder()
            .name(name)
            .description(description)
            .createdBy(author)
            .build());

        for (Seed seed : seeds) {
            Instant completedAt = seed.startedAt().plusSeconds(4);
            Deployment deployment = deploymentRepository.save(Deployment.builder()
                .project(project)
                .version(seed.version())
                .environment(seed.environment())
                .status(seed.status())
                .deployedBy(author)
                .startedAt(seed.startedAt())
                .completedAt(completedAt)
                .build());

            appendLog(deployment, LogLevel.INFO, seed.startedAt(),
                "Deployment of version " + seed.version() + " to " + seed.environment() + " started");
            appendLog(deployment, LogLevel.INFO, seed.startedAt().plusSeconds(1), "Pulling container image");
            appendLog(deployment, LogLevel.INFO, seed.startedAt().plusSeconds(2), "Running database migrations");

            if (seed.status() == DeploymentStatus.FAILED) {
                appendLog(deployment, LogLevel.WARN, seed.startedAt().plusSeconds(3),
                    "Slow response from /api/stock (450ms)");
                appendLog(deployment, LogLevel.ERROR, completedAt,
                    "Connection timeout to inventory-db replica after 30000ms");
                appendLog(deployment, LogLevel.ERROR, completedAt, "Deployment finished with status FAILED");
            } else {
                appendLog(deployment, LogLevel.INFO, seed.startedAt().plusSeconds(3),
                    "Waiting for health checks to pass");
                appendLog(deployment, LogLevel.INFO, completedAt, "Deployment finished with status SUCCESS");
            }
        }
    }

    private void appendLog(Deployment deployment, LogLevel level, Instant at, String message) {
        logRepository.save(LogEntry.builder()
            .deployment(deployment)
            .level(level)
            .message(message)
            .timestamp(at)
            .build());
    }
}
