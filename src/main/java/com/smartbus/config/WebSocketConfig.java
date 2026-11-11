package com.smartbus.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost:5174}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        List<String> origins = new ArrayList<>(Arrays.asList(allowedOrigins.split(",")));
        origins.add("http://192.168.*:*");
        origins.add("http://10.*:*");
        origins.add("http://172.*:*");
        origins.add("http://localhost:*");
        origins.add("https://*.vercel.app");
        String[] originPatterns = origins.toArray(new String[0]);

        registry.addEndpoint("/ws-tracking", "/ws")
                .setAllowedOriginPatterns(originPatterns)
                .withSockJS();
        registry.addEndpoint("/ws-tracking", "/ws")
                .setAllowedOriginPatterns(originPatterns);
    }
}
