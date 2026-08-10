package com.deploytrack.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deploytrack.entity.Role;
import com.deploytrack.entity.User;
import com.deploytrack.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminBootstrap bootstrapWith(String email, String username, String password) {
        return new AdminBootstrap(userRepository, passwordEncoder, email, username, password);
    }

    @Test
    void createsAdminWhenNoneExistsAndCredentialsProvided() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("admin@deploytrack.dev")).thenReturn(false);
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(passwordEncoder.encode("bootstrap-password")).thenReturn("$2a$10$hashed");

        bootstrapWith("admin@deploytrack.dev", "admin", "bootstrap-password").run();

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(captor.getValue().getEmail()).isEqualTo("admin@deploytrack.dev");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashed");
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        // The critical property: restarting the app must never overwrite or
        // reset an existing admin, or anyone who can set env vars could take
        // over the account.
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        bootstrapWith("admin@deploytrack.dev", "admin", "bootstrap-password").run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenCredentialsAreNotConfigured() {
        // An unconfigured deployment must not silently create an account with
        // a predictable password.
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        bootstrapWith("", "admin", "").run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenPasswordMissingButEmailProvided() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        bootstrapWith("admin@deploytrack.dev", "admin", "").run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void skipsWhenEmailAlreadyTakenByNonAdmin() {
        // Saving would violate the unique constraint and crash startup.
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("taken@deploytrack.dev")).thenReturn(true);

        bootstrapWith("taken@deploytrack.dev", "admin", "bootstrap-password").run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void skipsWhenUsernameAlreadyTakenByNonAdmin() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("fresh@deploytrack.dev")).thenReturn(false);
        when(userRepository.existsByUsername("admin")).thenReturn(true);

        bootstrapWith("fresh@deploytrack.dev", "admin", "bootstrap-password").run();

        verify(userRepository, never()).save(any());
    }
}
