package com.chatops.presence;

import com.chatops.room.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * REST endpoints for querying user presence.
 *
 * <p>Real-time presence changes are pushed over WebSocket to
 * {@code /topic/rooms/{id}/presence} by {@link com.chatops.websocket.WebSocketEventListener}.
 * These REST endpoints are for initial page load / polling fallback.
 */
@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;
    private final RoomService roomService;

    /**
     * Returns the online status of all currently-online users.
     *
     * @return set of usernames that are currently online
     */
    @GetMapping("/online")
    public ResponseEntity<Set<String>> getOnlineUsers() {
        return ResponseEntity.ok(presenceService.getAllOnlineUsers());
    }

    /**
     * Returns the online status of every member of a given room.
     * Caller must be a member of the room.
     *
     * @param roomId  the room to query
     * @param user    the authenticated caller (injected by Spring Security)
     * @return list of {@link PresenceService.UserPresence} records
     */
    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<List<PresenceService.UserPresence>> getRoomPresence(
            @PathVariable Long roomId,
            @AuthenticationPrincipal UserDetails user) {

        // Ensure the caller is a member of the room (throws ForbiddenException if not)
        roomService.getRoom(roomId, user.getUsername());

        List<String> members = roomService.getRoomMemberUsernames(roomId);
        return ResponseEntity.ok(presenceService.getBulkPresence(members));
    }

    /**
     * Renews the presence TTL heartbeat for the authenticated user.
     * Clients should call this every ~60 seconds to avoid being marked offline
     * due to TTL expiry without a disconnect event.
     *
     * @param user the authenticated caller
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@AuthenticationPrincipal UserDetails user) {
        presenceService.heartbeat(user.getUsername());
        return ResponseEntity.noContent().build();
    }
}
