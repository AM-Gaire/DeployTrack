package com.deploytrack.security;

import com.deploytrack.entity.User;
import com.deploytrack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Resolves the caller's User entity from the SecurityContext, which the JWT
// filter populated earlier in the request. Wrapping this in one service keeps
// SecurityContextHolder out of the business layer -- ProjectService should not
// need to know how authentication is transported, only who the caller is.
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User require() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // Reaching here means a protected endpoint was served without
            // authentication, which would be a SecurityConfig bug rather than
            // a client error -- hence a 500, not a 401.
            throw new IllegalStateException("No authenticated user in the security context");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalStateException(
                "Authenticated user " + email + " no longer exists"));
    }
}
