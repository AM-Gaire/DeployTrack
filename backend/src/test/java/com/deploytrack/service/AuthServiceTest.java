package com.deploytrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deploytrack.dto.LoginRequest;
import com.deploytrack.dto.RegisterRequest;
import com.deploytrack.entity.Role;
import com.deploytrack.entity.User;
import com.deploytrack.exception.DuplicateResourceException;
import com.deploytrack.exception.InvalidCredentialsException;
import com.deploytrack.repository.UserRepository;
import com.deploytrack.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerHashesPasswordAndNeverStoresPlaintext() {
        when(userRepository.existsByEmail("amrit@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("amrit")).thenReturn(false);
        when(passwordEncoder.encode("supersecret123")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(new RegisterRequest("amrit", "amrit@example.com", "supersecret123"));

        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashed");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("supersecret123");
    }

    @Test
    void registerAlwaysAssignsDeveloperRole() {
        when(userRepository.existsByEmail("amrit@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("amrit")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = authService.register(
            new RegisterRequest("amrit", "amrit@example.com", "supersecret123"));

        // Self-registration must never be able to produce an ADMIN.
        assertThat(result.role()).isEqualTo(Role.DEVELOPER);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
            new RegisterRequest("newuser", "taken@example.com", "supersecret123")))
            .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        User user = User.builder()
            .id(1L).username("amrit").email("amrit@example.com")
            .passwordHash("$2a$10$hashed").role(Role.DEVELOPER).build();
        when(userRepository.findByEmail("amrit@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("supersecret123", "$2a$10$hashed")).thenReturn(true);
        when(jwtService.generateToken("amrit@example.com", "DEVELOPER")).thenReturn("a.jwt.token");
        when(jwtService.getExpirySeconds()).thenReturn(900L);

        var response = authService.login(new LoginRequest("amrit@example.com", "supersecret123"));

        assertThat(response.accessToken()).isEqualTo("a.jwt.token");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.user().email()).isEqualTo("amrit@example.com");
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = User.builder()
            .email("amrit@example.com").passwordHash("$2a$10$hashed").role(Role.DEVELOPER).build();
        when(userRepository.findByEmail("amrit@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpassword", "$2a$10$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
            new LoginRequest("amrit@example.com", "wrongpassword")))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginGivesIdenticalErrorForUnknownEmailAndWrongPassword() {
        // Both paths must be indistinguishable, otherwise the API leaks which
        // email addresses have accounts.
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        User user = User.builder()
            .email("amrit@example.com").passwordHash("$2a$10$hashed").role(Role.DEVELOPER).build();
        when(userRepository.findByEmail("amrit@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpassword", "$2a$10$hashed")).thenReturn(false);

        String unknownEmailMessage = catchMessage(() ->
            authService.login(new LoginRequest("nobody@example.com", "supersecret123")));
        String wrongPasswordMessage = catchMessage(() ->
            authService.login(new LoginRequest("amrit@example.com", "wrongpassword")));

        assertThat(unknownEmailMessage).isEqualTo(wrongPasswordMessage);
    }

    private String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected InvalidCredentialsException");
        } catch (InvalidCredentialsException ex) {
            return ex.getMessage();
        }
    }
}
