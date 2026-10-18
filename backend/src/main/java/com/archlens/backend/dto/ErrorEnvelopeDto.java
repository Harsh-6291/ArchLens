package com.archlens.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEnvelopeDto(ErrorDetails error) {

    public static ErrorEnvelopeDto of(String code, String message) {
        return new ErrorEnvelopeDto(new ErrorDetails(code, message, Collections.emptyMap()));
    }

    public static ErrorEnvelopeDto of(String code, String message, Map<String, Object> details) {
        return new ErrorEnvelopeDto(new ErrorDetails(code, message, details != null ? details : Collections.emptyMap()));
    }

    public record ErrorDetails(
            String code,
            String message,
            Map<String, Object> details
    ) {}
}
