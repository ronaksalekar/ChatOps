package com.chatops.auth.dto;

/**
 * Response returned after a successful login or token refresh.
 *
 * @param accessToken  short-lived JWT for API calls (Bearer token)
 * @param refreshToken long-lived opaque token for obtaining new access tokens
 * @param tokenType    always {@code "Bearer"}
 * @param expiresIn    access token lifetime in seconds
 * @param userId       the authenticated user's ID
 * @param username     the authenticated user's username
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String username
) {
    public static AuthResponse of(String accessToken, String refreshToken,
                                  long expiresInMs, Long userId, String username) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresInMs / 1000,
                userId,
                username
        );
    }
}
