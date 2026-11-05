package com.chatops.message.dto;

import com.chatops.message.MessageType;

import java.time.Instant;

/**
 * Message payload returned in history and real-time responses.
 *
 * @param id              internal message ID
 * @param cursorId        stable pagination cursor value
 * @param roomId          the room this message belongs to (null for DMs)
 * @param senderId        the sender's user ID
 * @param senderUsername  the sender's username
 * @param content         message text
 * @param messageType     TEXT | JOIN | LEAVE | SYSTEM
 * @param createdAt       when the message was sent
 */
public record MessageResponse(
        Long id,
        Long cursorId,
        Long roomId,
        Long senderId,
        String senderUsername,
        String content,
        MessageType messageType,
        Instant createdAt
) {}
