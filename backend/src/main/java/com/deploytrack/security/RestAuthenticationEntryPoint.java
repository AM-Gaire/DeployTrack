package com.deploytrack.security;

import com.deploytrack.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

// Without this, an unauthenticated request gets Spring Security's default
// response, which is an HTML login redirect -- useless to a React client
// expecting JSON. This returns the same ErrorResponse shape as every other
// error in the API, so the frontend can handle 401 generically.
//
// Note this cannot live in GlobalExceptionHandler: security rejections happen
// in the filter chain, before the request ever reaches a controller, so
// @RestControllerAdvice never sees them.
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException {
        var body = new ErrorResponse(
            Instant.now(),
            HttpStatus.UNAUTHORIZED.value(),
            HttpStatus.UNAUTHORIZED.getReasonPhrase(),
            "Authentication required. Provide a valid token.",
            request.getRequestURI()
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
