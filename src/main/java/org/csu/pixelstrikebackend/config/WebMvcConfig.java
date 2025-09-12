package org.csu.pixelstrikebackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer { // <--- 接口从 WebFluxConfigurer 改为 WebMvcConfigurer

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /uploads/** 请求映射到项目根目录下的 uploads 文件夹
        // 注意这里的路径格式 "file:"
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
}