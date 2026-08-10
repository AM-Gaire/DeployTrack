package com.deploytrack.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Runs before the controller on every request. Its only job is: if a valid
// token is present, tell Spring Security who the caller is. It never rejects
// anything itself -- an unauthenticated request simply passes through with an
// empty SecurityContext, and SecurityConfig's rules decide whether that is
// allowed. Keeping "who are you" separate from "are you allowed" is what makes
// public endpoints like /auth/login work through the same filter.
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIX.length());

        // Only populate the context if it is empty -- if something earlier in
        // the chain already authenticated this request, don't overwrite it.
        if (jwtService.isValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            var userDetails = userDetailsService.loadUserByUsername(jwtService.extractEmail(token));

            var authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                // Credentials are null: the token already proved identity,
                // so there is no password to carry around here.
                null,
                userDetails.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
