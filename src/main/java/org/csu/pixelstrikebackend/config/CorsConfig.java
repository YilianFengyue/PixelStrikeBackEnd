// 文件路径: src/main/java/org/csu/pixelstrikebackend/config/CorsConfig.java

package org.csu.pixelstrikebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // 允许所有路径的请求
                registry.addMapping("/**")
                        // 允许所有来源
                        .allowedOrigins("*")
                        // 允许所有HTTP方法 (GET, POST, PUT, DELETE, etc.)
                        .allowedMethods("*")
                        // 允许所有请求头
                        .allowedHeaders("*");
            }
        };
    }
}