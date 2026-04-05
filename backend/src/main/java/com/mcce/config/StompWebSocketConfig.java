package com.mcce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class StompWebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final String[] allowedOriginPatterns;
	private final CollabStompSessionOutboundInterceptor collabStompSessionOutboundInterceptor;

	public StompWebSocketConfig(
		@Value("${mcce.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String[] allowedOriginPatterns,
		CollabStompSessionOutboundInterceptor collabStompSessionOutboundInterceptor
	) {
		this.allowedOriginPatterns = allowedOriginPatterns;
		this.collabStompSessionOutboundInterceptor = collabStompSessionOutboundInterceptor;
	}

	@Override
	public void configureClientOutboundChannel(ChannelRegistration registration) {
		registration.interceptors(collabStompSessionOutboundInterceptor);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
		registry.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry
			.addEndpoint("/ws-collab")
			.setAllowedOriginPatterns(allowedOriginPatterns);
	}
}
