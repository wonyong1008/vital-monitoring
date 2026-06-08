package com.aitrics.vitalmonitoring.interfaces.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(
        String access_token,
        String refresh_token,
        String token_type,
        long expires_in
) {
    public static TokenResponse of(String accessToken, String refreshToken, long expiresInMs) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresInMs / 1000);
    }

    public static TokenResponse ofAccess(String accessToken, long expiresInMs) {
        return new TokenResponse(accessToken, null, "Bearer", expiresInMs / 1000);
    }
}
