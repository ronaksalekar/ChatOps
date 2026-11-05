package com.chatops.config;

import com.chatops.websocket.StompChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures the WebSocket message broker.
 *
 * <p>Clients connect via SockJS at {@code /ws}, then subscribe to topics like
 * {@code /topic/rooms/{id}} for broadcast messages and {@code /user/queue/errors}
 * for private error replies.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompChannelInterceptor stompChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Simple in-memory broker for topic (broadcast) and queue (point-to-point)
        registry.enableSimpleBroker("/topic", "/queue");

        // Client sends messages to /app/... (routed to @MessageMapping methods)
        registry.setApplicationDestinationPrefixes("/app");

        // Server sends private messages to /user/{username}/queue/...
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Validate JWT on every inbound STOMP frame
        registration.interceptors(stompChannelInterceptor);
    }
}
