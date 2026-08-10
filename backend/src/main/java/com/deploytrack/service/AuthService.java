package com.deploytrack.service;

import com.deploytrack.dto.LoginRequest;
import com.deploytrack.dto.LoginResponse;
import com.deploytrack.dto.RegisterRequest;
import com.deploytrack.dto.UserSummary;
import com.deploytrack.entity.Role;
import com.deploytrack.entity.User;
import com.deploytrack.exception.DuplicateResourceException;
import com.deploytrack.exception.InvalidCredentialsException;
import com.deploytrack.repository.UserRepository;
import com.deploytrack.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public UserSummary register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("That email is already registered");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("That username is already taken");
        }

        User user = User.builder()
            .username(request.username())
            .email(request.email())
            // The raw password is hashed here and never stored, logged, or
            // returned. This is the only place it exists in memory at all.
            .passwordHash(passwordEncoder.encode(request.password()))
            // Role is assigned server-side, never taken from the request --
            // otherwise anyone could register themselves as an ADMIN.
            .role(Role.DEVELOPER)
            .build();

        return UserSummary.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Same message and status as the unknown-email case above, on
            // purpose. Distinguishing "no such user" from "wrong password"
            // would let an attacker enumerate which emails have accounts.
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new LoginResponse(token, jwtService.getExpirySeconds(), UserSummary.from(user));
    }
}
