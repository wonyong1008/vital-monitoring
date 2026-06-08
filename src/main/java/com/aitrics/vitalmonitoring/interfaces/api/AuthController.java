package com.aitrics.vitalmonitoring.interfaces.api;

import com.aitrics.vitalmonitoring.infrastructure.security.JwtTokenProvider;
import com.aitrics.vitalmonitoring.interfaces.dto.request.LoginRequest;
import com.aitrics.vitalmonitoring.interfaces.dto.request.RefreshTokenRequest;
import com.aitrics.vitalmonitoring.interfaces.dto.response.ErrorResponse;
import com.aitrics.vitalmonitoring.interfaces.dto.response.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.auth.username}")
    private String validUsername;

    @Value("${app.auth.password}")
    private String validPassword;

    @Value("${app.jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    @PostMapping("/login")
    @Operation(summary = "로그인 - Access Token 및 Refresh Token 발급")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest request) {
        if (!validUsername.equals(request.username()) || !validPassword.equals(request.password())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.of(HttpStatus.UNAUTHORIZED.value(), "아이디 또는 비밀번호가 올바르지 않습니다."));
        }

        String accessToken = jwtTokenProvider.generateAccessToken(request.username());
        String refreshToken = jwtTokenProvider.generateRefreshToken(request.username());

        return ResponseEntity.ok(TokenResponse.of(accessToken, refreshToken, accessTokenExpiryMs));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Access Token 재발급")
    public ResponseEntity<?> refresh(@RequestBody @Valid RefreshTokenRequest request) {
        String token = request.refresh_token();

        if (!jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isRefreshToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.of(HttpStatus.UNAUTHORIZED.value(), "유효하지 않은 Refresh Token입니다."));
        }

        String username = jwtTokenProvider.getUsername(token);
        String newAccessToken = jwtTokenProvider.generateAccessToken(username);

        return ResponseEntity.ok(TokenResponse.ofAccess(newAccessToken, accessTokenExpiryMs));
    }
}
