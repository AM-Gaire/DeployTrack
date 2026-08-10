package com.deploytrack.security;

import com.deploytrack.entity.User;
import com.deploytrack.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// The adapter between our domain (User entity) and Spring Security's world
// (UserDetails). Spring Security has no idea what our User class is, so we
// translate. Login is by email, so that is what we look up on.
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("No user found for " + email));

        // Spring Security's convention is that role authorities are prefixed
        // with "ROLE_". hasRole("ADMIN") checks for authority "ROLE_ADMIN" --
        // omit the prefix here and every role check silently fails.
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        return new org.springframework.security.core.userdetails.User(
            user.getEmail(),
            user.getPasswordHash(),
            authorities
        );
    }
}
