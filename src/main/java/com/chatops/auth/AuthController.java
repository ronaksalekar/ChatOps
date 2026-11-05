package com.chatops.auth;

import com.chatops.auth.dto.AuthResponse;
import com.chatops.auth.dto.LoginRequest;
import com.chatops.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for authentication operations.
 *
 * <p>All endpoints under {@code /api/auth} are publicly accessible (no JWT required).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, refresh, and logout")
public class AuthController {

    private final AuthService authService;

    /**
     * Registers a new user account.
     *
     * @param request registration payload
     * @return 201 Created with tokens
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /**
     * Authenticates a user and returns JWT tokens.
     *
     * @param request login credentials
     * @return 200 OK with tokens
     */
    @PostMapping("/login")
    @Operation(summary = "Login with username and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Issues a new access token using a valid refresh token.
     * The old refresh token is rotated (deleted) and a new one is issued.
     *
     * @param body JSON body containing {@code "refreshToken"}
     * @return 200 OK with new tokens
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (!StringUtils.hasText(refreshToken)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(authService.refresh(refreshToken));
    }

    /**
     * Logs out the current user by revoking both tokens.
     *
     * @param authorizationHeader the {@code Authorization: Bearer <token>} header
     * @param body                JSON body optionally containing {@code "refreshToken"}
     * @return 204 No Content
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout and revoke tokens")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody(required = false) Map<String, String> body
    ) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().build();
        }
        String accessToken = authorizationHeader.substring(7);
        String refreshToken = (body != null) ? body.get("refreshToken") : null;
        authService.logout(accessToken, refreshToken);
        return ResponseEntity.noContent().build();
    }
}
