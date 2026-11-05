package com.chatops.message;

import com.chatops.room.Room;
import com.chatops.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A chat message sent either to a room or directly to another user.
 * {@code cursorId} is a monotonically increasing value used for efficient cursor-based pagination.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Monotonically increasing cursor used for stable pagination.
     * Populated by the DB sequence; never set by application code.
     */
    @Column(name = "cursor_id", insertable = false, updatable = false)
    private Long cursorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dm_recipient_id")
    private User dmRecipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
