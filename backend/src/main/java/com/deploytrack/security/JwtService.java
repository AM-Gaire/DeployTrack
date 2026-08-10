package com.deploytrack.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Owns everything about the token itself: creating one, reading claims out of
// one, and deciding whether one is still trustworthy. Nothing else in the
// codebase should touch the JJWT API directly -- that keeps the library
// swappable and the security-critical logic in a single reviewable place.
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration expiry;

    public JwtService(
        @Value("${deploytrack.jwt.secret}") String secret,
        @Value("${deploytrack.jwt.expiry}") Duration expiry
    ) {
        // HS256 requires a key of at least 256 bits. Keys.hmacShaKeyFor
        // enforces that and fails fast at startup on a too-short secret,
        // rather than silently accepting a weak key.
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expiry = expiry;
    }

    public String generateToken(String email, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
            // "sub" (subject) is the standard claim for who the token is about.
            .subject(email)
            // Role travels in the token so authorization needs no DB lookup.
            // Safe to expose: the payload is readable by anyone, but the
            // signature means it cannot be altered without detection.
            .claim("role", role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(expiry)))
            .signWith(signingKey)
            .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            // Covers a bad signature, a malformed token, and an expired one.
            // The specific reason is deliberately not surfaced to the client:
            // telling an attacker *why* a token failed is free information.
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public long getExpirySeconds() {
        return expiry.toSeconds();
    }
}
