package com.deploytrack.controller;

import com.deploytrack.dto.UserSummary;
import com.deploytrack.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final CurrentUserService currentUserService;

    // Lets the frontend recover the session on page refresh: it holds a token
    // in storage but no user object, so it calls this to find out who it is
    // and which role-gated UI to render.
    @GetMapping("/me")
    public UserSummary me() {
        return UserSummary.from(currentUserService.require());
    }
}
