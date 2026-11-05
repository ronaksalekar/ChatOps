package com.chatops.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for user login.
 *
 * @param username the user's login name
 * @param password the user's plaintext password
 */
public record LoginRequest(

        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password
) {}
