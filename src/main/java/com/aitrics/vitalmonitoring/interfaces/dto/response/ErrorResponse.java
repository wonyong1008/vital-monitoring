package com.aitrics.vitalmonitoring.interfaces.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String message,
        List<String> errors,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, null, LocalDateTime.now());
    }

    public static ErrorResponse of(int status, String message, List<String> errors) {
        return new ErrorResponse(status, message, errors, LocalDateTime.now());
    }
}
