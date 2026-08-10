package com.deploytrack.config;

import com.deploytrack.entity.Role;
import com.deploytrack.entity.User;
import com.deploytrack.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Solves a genuine chicken-and-egg problem: self-registration always assigns
// DEVELOPER (a client must never pick its own privileges), so without this
// a fresh database can never produce an ADMIN at all. Jenkins, GitLab and
// Keycloak all bootstrap their first admin the same way.
//
// This is NOT the DevDataSeeder hack that Phase 3 deleted. That one invented
// a fake user to stand in for authentication that did not exist yet. This is
// a permanent, config-driven answer to "how does the first admin exist?".
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminUsername;
    private final String adminPassword;

    public AdminBootstrap(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Value("${deploytrack.bootstrap.admin-email:}") String adminEmail,
        @Value("${deploytrack.bootstrap.admin-username:admin}") String adminUsername,
        @Value("${deploytrack.bootstrap.admin-password:}") String adminPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Idempotent by design: this runs on every startup, so it must be
        // safe to run repeatedly. Once any admin exists we never touch it --
        // in particular we never reset an existing admin's password, or an
        // attacker who could set env vars could silently take over an account.
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("""
                No ADMIN user exists and bootstrap credentials are not configured. \
                Admin-only actions will be unavailable. Set ADMIN_EMAIL and \
                ADMIN_PASSWORD and restart to create the first admin.""");
            return;
        }

        // Guard against colliding with a self-registered DEVELOPER who already
        // claimed this email or username -- saving would violate the unique
        // constraints and crash startup with an opaque error.
        if (userRepository.existsByEmail(adminEmail) || userRepository.existsByUsername(adminUsername)) {
            log.warn(
                "Cannot bootstrap admin: email '{}' or username '{}' is already taken by a non-admin user.",
                adminEmail, adminUsername);
            return;
        }

        User admin = User.builder()
            .username(adminUsername)
            .email(adminEmail)
            .passwordHash(passwordEncoder.encode(adminPassword))
            .role(Role.ADMIN)
            .build();
        userRepository.save(admin);

        // The email identifies which account was created; the password is
        // never logged, because application logs are routinely shipped to
        // places far less protected than the database.
        log.info("Bootstrapped initial ADMIN user '{}'. Change this password after first login.", adminEmail);
    }
}
