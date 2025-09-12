package org.csu.pixelstrikebackend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity // 1. 注解从 @EnableWebFluxSecurity 改为 @EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private AuthFilter authFilter; // 注入我们之前创建的 Servlet Filter

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    // 2. 返回类型和参数类型都换成 Servlet 版本
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 启用CORS，并使用默认配置 (会应用 CorsConfig 中的配置)
                .cors(withDefaults())
                // 禁用 CSRF，因为我们使用 JWT，是无状态的
                .csrf(csrf -> csrf.disable())

                // 将我们的自定义 AuthFilter 添加到 Spring Security 过滤器链中
                // 放在 UsernamePasswordAuthenticationFilter 之前执行
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)

                // 配置会话管理为无状态(STATELESS)，因为我们不使用 HttpSession
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 配置 HTTP 请求的授权规则
                .authorizeHttpRequests(auth -> auth
                        // 明确允许对认证、注册、文件上传和WebSocket握手路径的匿名访问
                        .requestMatchers("/auth/login", "/auth/register", "/uploads/**",
                                "/game-data/**",
                                "/users/reset-password",
                                "/ws/**", "/game/**").permitAll()
                        // 除了上面允许的路径，其他所有请求都需要认证,
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}