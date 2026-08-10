package com.deploytrack.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
// Enables @PreAuthorize on service/controller methods, which is how the
// permission matrix in docs/requirements.md gets enforced per-endpoint.
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF protects cookie-based sessions from cross-site form posts.
            // We authenticate with a header-borne token that a browser will
            // not attach automatically, so the attack does not apply and the
            // token check would just break non-browser clients.
            .csrf(csrf -> csrf.disable())

            // No server-side session at all: every request must carry its own
            // proof of identity. This is what "stateless" means in practice,
            // and it is why any instance behind a load balancer can serve any
            // request without a shared session store.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))

            .authorizeHttpRequests(auth -> auth
                // Registration and login must be reachable without a token --
                // otherwise there would be no way to ever obtain one.
                .requestMatchers("/api/auth/**").permitAll()
                // Preflight requests carry no credentials by design.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Default-deny: anything not explicitly permitted above needs
                // authentication. Listing public routes and denying the rest
                // is far safer than listing protected routes and permitting
                // the rest, where a forgotten endpoint is silently exposed.
                .anyRequest().authenticated())

            // Our filter must run before the username/password filter so the
            // SecurityContext is already populated by the time authorization
            // rules are evaluated.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt is deliberately slow and salts each hash automatically, so
        // identical passwords produce different hashes and brute-forcing a
        // leaked table stays expensive. The work factor can be raised later
        // as hardware gets faster without changing any calling code.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
