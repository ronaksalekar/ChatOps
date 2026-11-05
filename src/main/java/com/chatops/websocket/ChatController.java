package com.chatops.websocket;

import com.chatops.message.Message;
import com.chatops.message.MessageService;
import com.chatops.message.MessageType;
import com.chatops.message.dto.MessageResponse;
import com.chatops.room.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

/**
 * Handles real-time chat messages sent over STOMP.
 *
 * <p>Clients publish to {@code /app/rooms/{roomId}/send} and all room members
 * subscribed to {@code /topic/rooms/{roomId}} receive the broadcast.
 *
 * <p>System JOIN/LEAVE events are broadcast automatically when users connect or
 * disconnect (see {@link WebSocketEventListener}).
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final RoomService roomService;

    /**
     * Receives a chat message from a connected client, persists it, then
     * broadcasts it to all subscribers of the room topic.
     *
     * @param roomId  path variable from the STOMP destination
     * @param payload the inbound message payload
     * @param principal the authenticated sender
     */
    @MessageMapping("/rooms/{roomId}/send")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Payload ChatMessageRequest payload,
            Principal principal) {

        if (principal == null) {
            log.warn("Unauthenticated send attempt to room {}", roomId);
            return;
        }

        String username = principal.getName();

        // Authorization: sender must be a member of the room
        if (!roomService.isMember(roomId, username)) {
            log.warn("User {} tried to send to room {} without membership", username, roomId);
            messagingTemplate.convertAndSendToUser(
                    username, "/queue/errors",
                    new ErrorMessage("You are not a member of room " + roomId));
            return;
        }

        // Persist and broadcast
        MessageResponse saved = messageService.saveMessage(roomId, username, payload.content());
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId, saved);

        log.debug("User {} sent message to room {}: cursorId={}", username, roomId, saved.cursorId());
    }

    /**
     * Broadcasts a typing indicator to all room subscribers.
     * Typing events are ephemeral — they are NOT persisted.
     *
     * @param roomId    path variable from the STOMP destination
     * @param principal the authenticated user who is typing
     */
    @MessageMapping("/rooms/{roomId}/typing")
    public void typingIndicator(
            @DestinationVariable Long roomId,
            Principal principal) {

        if (principal == null) return;

        String username = principal.getName();

        if (!roomService.isMember(roomId, username)) return;

        TypingEvent event = new TypingEvent(roomId, username, Instant.now().toEpochMilli());
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/typing", event);
    }

    // ── Payload records ──────────────────────────────────────────────────────

    /** Inbound payload sent by the client when posting a chat message. */
    public record ChatMessageRequest(String content) {}

    /** Outbound typing indicator broadcast to room subscribers. */
    public record TypingEvent(Long roomId, String username, long timestamp) {}

    /** Private error message sent back to the offending user. */
    public record ErrorMessage(String error) {}
}
