package com.aitrics.vitalmonitoring.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "refresh_token은 필수입니다.")
        String refresh_token
) {}
