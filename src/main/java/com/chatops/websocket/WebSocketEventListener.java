package com.chatops.websocket;

import com.chatops.presence.PresenceService;
import com.chatops.room.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;

/**
 * Listens to Spring's WebSocket lifecycle events and keeps Redis presence
 * in sync. When a user connects, their presence is marked online; when they
 * disconnect, it is removed and a LEAVE system message is broadcast to every
 * room they belong to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PresenceService presenceService;
    private final RoomMemberRepository roomMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        String sessionId = accessor.getSessionId();
        if (principal == null || sessionId == null) return;

        String username = principal.getName();
        boolean becameOnline = presenceService.markOnline(username, sessionId);
        log.info("WebSocket connected: {}", username);

        // Notify all rooms the user belongs to
        if (becameOnline) broadcastPresence(username, "ONLINE");
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        String sessionId = accessor.getSessionId();
        if (principal == null || sessionId == null) return;

        String username = principal.getName();
        boolean becameOffline = presenceService.markOffline(username, sessionId);
        log.info("WebSocket disconnected: {}", username);

        if (becameOffline) broadcastPresence(username, "OFFLINE");
    }

    @Scheduled(fixedDelay = 15_000)
    public void evictStaleSessions() {
        presenceService.evictStalePresence()
                .forEach(username -> broadcastPresence(username, "OFFLINE"));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void broadcastPresence(String username, String status) {
        List<Long> roomIds = roomMemberRepository.findRoomIdsByUsername(username);
        PresenceEvent event = new PresenceEvent(username, status);
        for (Long roomId : roomIds) {
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/presence", event);
        }
    }

    /** Outbound presence status change event broadcast to room subscribers. */
    public record PresenceEvent(String username, String status) {}
}
