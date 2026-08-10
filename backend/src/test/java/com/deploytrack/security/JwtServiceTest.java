package com.deploytrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    // A throwaway 256-bit key for tests only -- never the one the app runs with.
    private static final String TEST_SECRET = "dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLXVuaXQtdGVzdHMh";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, Duration.ofMinutes(15));
    }

    @Test
    void generatedTokenCarriesEmailAndRole() {
        String token = jwtService.generateToken("amrit@example.com", "DEVELOPER");

        assertThat(jwtService.extractEmail(token)).isEqualTo("amrit@example.com");
        assertThat(jwtService.extractRole(token)).isEqualTo("DEVELOPER");
        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    void rejectsExpiredToken() {
        // A negative expiry produces a token that was already expired when issued.
        var expiredService = new JwtService(TEST_SECRET, Duration.ofMinutes(-1));
        String expired = expiredService.generateToken("amrit@example.com", "DEVELOPER");

        assertThat(expiredService.isValid(expired)).isFalse();
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        // This is the property the whole scheme rests on: a token forged with
        // any other key must not verify, no matter how well-formed it is.
        var attackerService = new JwtService(
            "YW4tZW50aXJlbHktZGlmZmVyZW50LXNlY3JldC1rZXktISE=", Duration.ofMinutes(15));
        String forged = attackerService.generateToken("attacker@example.com", "ADMIN");

        assertThat(jwtService.isValid(forged)).isFalse();
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken("amrit@example.com", "VIEWER");
        // Flip a character in the payload segment to simulate someone editing
        // their role claim. The signature no longer matches the payload.
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "XY." + parts[2];

        assertThat(jwtService.isValid(tampered)).isFalse();
    }

    @Test
    void rejectsGarbageInput() {
        assertThat(jwtService.isValid("not-a-token")).isFalse();
        assertThat(jwtService.isValid("")).isFalse();
    }
}
