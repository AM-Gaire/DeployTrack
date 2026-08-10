package com.deploytrack.dto;

import java.time.Instant;
import java.util.List;

public record ValidationErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {}
}
