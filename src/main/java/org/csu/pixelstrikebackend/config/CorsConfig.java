package org.csu.pixelstrikebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() { // <-- 方法名和返回类型已修改
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(List.of("*")); // 允许所有来源
        corsConfig.setMaxAge(3600L); // 预检请求的缓存时间
        corsConfig.addAllowedMethod("*"); // 允许所有HTTP方法
        corsConfig.addAllowedHeader("*"); // 允许所有请求头

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig); // 对所有路径应用此配置

        return source; // <-- 直接返回 source 对象
    }
}