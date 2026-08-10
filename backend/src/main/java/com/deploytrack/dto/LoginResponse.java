package com.deploytrack.dto;

public record LoginResponse(String accessToken, long expiresIn, UserSummary user) {}
