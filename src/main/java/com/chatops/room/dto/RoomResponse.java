package com.chatops.room.dto;

import java.time.Instant;

/**
 * Room summary returned in list and detail responses.
 *
 * @param id          the room ID
 * @param name        the room name
 * @param description optional room description
 * @param createdById the user ID of the room creator
 * @param isPrivate   whether the room is private/invite-only
 * @param memberCount number of current members
 * @param createdAt   when the room was created
 */
public record RoomResponse(
        Long id,
        String name,
        String description,
        Long createdById,
        String createdByUsername,
        boolean isPrivate,
        String inviteCode,
        long memberCount,
        Instant createdAt
) {}
