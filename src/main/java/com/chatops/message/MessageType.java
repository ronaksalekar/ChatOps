package com.chatops.message;

/**
 * Describes the intent of a chat message.
 */
public enum MessageType {
    /** Regular user-typed text message */
    TEXT,
    /** System event: user joined the room */
    JOIN,
    /** System event: user left the room */
    LEAVE,
    /** Generic system/admin announcement */
    SYSTEM
}
