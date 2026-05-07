package com.hunor.classicmodelsbackend.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Sets up STOMP-over-WebSocket messaging.
 *
 * <h3>What is STOMP?</h3>
 *
 * <p>WebSocket is a transport — bidirectional bytes between browser and
 * server. STOMP (Simple/Streaming Text Oriented Messaging Protocol) is
 * a small frame-based protocol layered on top, giving us
 * "subscribe to topic" and "send to destination" semantics. Without
 * STOMP you'd hand-design those primitives yourself; with STOMP we get
 * them for free, and clients in many languages already speak it.</p>
 *
 * <h3>The two destinations</h3>
 *
 * <ul>
 *   <li><b>{@code /topic/...}</b> — topics, broadcast to every subscriber.
 *       We push employee events here.</li>
 *   <li><b>{@code /app/...}</b> — application destinations, routed to
 *       {@code @MessageMapping} controller methods. We don't use these
 *       (clients are read-only consumers); kept in the config to show
 *       where you'd add them.</li>
 * </ul>
 *
 * <h3>Auth note</h3>
 *
 * <p>This config does NOT authenticate WebSocket connections. The
 * {@link com.hunor.classicmodelsbackend.security.SecurityConfig}
 * permits {@code /ws/**} so any client can connect. That's fine for
 * a learning project where the broadcast events are non-sensitive
 * (just "an employee changed, reload"), and stays consistent with the
 * permitted-static-tooling endpoints. For production you'd add a STOMP
 * channel interceptor that reads the JWT from the CONNECT frame's
 * Authorization header.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple in-memory broker. Topics whose names start
        // with /topic are broadcast destinations. There's also a more
        // production-grade external broker option (RabbitMQ, ActiveMQ);
        // the in-memory broker is fine until you have multiple backend
        // instances.
        config.enableSimpleBroker("/topic");

        // Prefix for messages sent FROM clients TO @MessageMapping
        // handlers. Unused by us right now but configured for symmetry.
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // The connection URL is /ws (under the app's /api/v1
        // context-path → final URL is /api/v1/ws). Clients open a
        // WebSocket here, then send a STOMP CONNECT frame to start
        // the messaging session.
        //
        // setAllowedOriginPatterns("*") permits cross-origin
        // connections, needed because the Angular dev server runs on
        // :4200 while the backend runs on :9090. In production you'd
        // tighten this to the known frontend origin.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}
