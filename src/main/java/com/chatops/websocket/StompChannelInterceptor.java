package com.chatops.websocket;

import com.chatops.auth.JwtService;
import com.chatops.presence.PresenceService;
import com.chatops.room.RoomMemberRepository;
import com.chatops.room.RoomService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intercepts inbound STOMP frames and authenticates the connection using a JWT
 * token passed in the {@code Authorization} header of the CONNECT frame.
 *
 * <p>Once authenticated, the {@link org.springframework.security.core.Authentication}
 * is set on the accessor so Spring's user-destination resolution ({@code /user/...})
 * works correctly for private message routing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

    private static final Pattern ROOM_TOPIC =
            Pattern.compile("^/topic/rooms/(\\d+)(?:/(?:typing|presence))?$");

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RoomService roomService;
    private final PresenceService presenceService;
    private final RoomMemberRepository roomMemberRepository;
    private final ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // Only authenticate on CONNECT — subsequent frames inherit the principal
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    Claims claims = jwtService.validateAndExtractClaims(token);
                    String username = claims.getSubject();

                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());

                    accessor.setUser(auth);
                    log.debug("WebSocket authenticated: {}", username);
                } catch (Exception e) {
                    log.warn("WebSocket JWT validation failed: {}", e.getMessage());
                    // Returning null rejects the frame and closes the connection
                    return null;
                }
            } else {
                log.warn("WebSocket CONNECT missing Authorization header — rejecting");
                return null;
            }
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/topic/rooms/")) {
                Matcher matcher = ROOM_TOPIC.matcher(destination);
                if (!matcher.matches() || accessor.getUser() == null) {
                    return null;
                }
                try {
                    long roomId = Long.parseLong(matcher.group(1));
                    if (!roomService.isMember(roomId, accessor.getUser().getName())) {
                        log.warn("Rejected room-topic subscription: user={} room={}",
                                accessor.getUser().getName(), roomId);
                        return null;
                    }
                } catch (RuntimeException e) {
                    log.warn("Rejected invalid room-topic subscription: {}", destination);
                    return null;
                }
            }
        }

        // Refresh only this authenticated STOMP session's presence lease.
        if (!StompCommand.CONNECT.equals(accessor.getCommand())
                && accessor.getUser() != null && accessor.getSessionId() != null) {
            String username = accessor.getUser().getName();
            if (presenceService.heartbeat(username, accessor.getSessionId())) {
                PresenceEvent event = new PresenceEvent(username, "ONLINE");
                messagingTemplateProvider.ifAvailable(template ->
                        roomMemberRepository.findRoomIdsByUsername(username).forEach(roomId ->
                                template.convertAndSend(
                                        "/topic/rooms/" + roomId + "/presence", event)));
            }
        }

        return message;
    }

    private record PresenceEvent(String username, String status) {}
}
