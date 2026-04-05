package com.mcce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final String[] allowedOriginPatterns;

	public WebConfig(
		@Value("${mcce.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String[] allowedOriginPatterns
	) {
		this.allowedOriginPatterns = allowedOriginPatterns;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry
			.addMapping("/api/**")
			.allowedOriginPatterns(allowedOriginPatterns)
			.allowedMethods("GET", "POST", "OPTIONS")
			.allowedHeaders("*")
			.allowCredentials(false);
	}
}
