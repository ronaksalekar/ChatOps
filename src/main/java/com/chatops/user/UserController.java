package com.chatops.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * REST controller for user profile operations.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile operations")
public class UserController {

    /**
     * Returns the currently authenticated user's profile.
     */
    @GetMapping("/me")
    @Operation(summary = "Get the current user's profile")
    public ResponseEntity<UserProfileResponse> getMe(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(UserProfileResponse.from(currentUser));
    }

    /**
     * Profile response DTO.
     */
    public record UserProfileResponse(
            Long id,
            String username,
            String email,
            String displayName,
            String avatarUrl,
            Instant createdAt
    ) {
        public static UserProfileResponse from(User user) {
            return new UserProfileResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getAvatarUrl(),
                    user.getCreatedAt()
            );
        }
    }
}
