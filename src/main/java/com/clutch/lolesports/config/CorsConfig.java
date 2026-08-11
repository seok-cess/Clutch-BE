package com.clutch.lolesports.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 개발용 CORS 허용 (Vite dev server).
 * 실제로는 Vite proxy 로 /api 를 프록시하므로 필수는 아니지만,
 * 프록시 없이 직접 호출하는 경우를 위해 열어둔다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET");
    }
}
