package jp.workwith.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AuthenticatedHandshakeInterceptor handshakeInterceptor;
    private final AuthenticatedHandshakeHandler handshakeHandler;
    private final RoomSubscriptionAuthorizationInterceptor subscriptionAuthorizationInterceptor;

    public WebSocketConfig(
            AuthenticatedHandshakeInterceptor handshakeInterceptor,
            AuthenticatedHandshakeHandler handshakeHandler,
            RoomSubscriptionAuthorizationInterceptor subscriptionAuthorizationInterceptor) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.handshakeHandler = handshakeHandler;
        this.subscriptionAuthorizationInterceptor = subscriptionAuthorizationInterceptor;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(subscriptionAuthorizationInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(handshakeHandler)
                .addInterceptors(handshakeInterceptor);
    }
}
