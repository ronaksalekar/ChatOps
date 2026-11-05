package com.chatops.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new room.
 *
 * @param name        the room's display name (required, 1–100 chars)
 * @param description optional description
 * @param isPrivate   whether the room is invite-only
 */
public record CreateRoomRequest(

        @NotBlank(message = "Room name is required")
        @Size(min = 1, max = 100, message = "Room name must be between 1 and 100 characters")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        boolean isPrivate
) {}
