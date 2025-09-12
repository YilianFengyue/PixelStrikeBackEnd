package org.csu.pixelstrikebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource; // <-- Use this import

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    // The method signature and return type are now correct for the Servlet stack
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow requests from any origin. For production, you might want to restrict this.
        configuration.setAllowedOrigins(List.of("*"));
        // Allow all standard HTTP methods
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // Allow all headers
        configuration.setAllowedHeaders(List.of("*"));
        // Allow credentials
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(); // <-- Use this implementation
        source.registerCorsConfiguration("/**", configuration); // Apply this configuration to all paths
        return source;
    }
}