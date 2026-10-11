package com.nexora.restopos.config;

import com.nexora.restopos.security.PublicRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Aplica {@link PublicRateLimitInterceptor} a la API pública y al login / recuperación del personal. */
@Configuration
@RequiredArgsConstructor
public class PublicRateLimitConfig implements WebMvcConfigurer {

    private final PublicRateLimitInterceptor publicRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(publicRateLimitInterceptor)
                .addPathPatterns("/api/public/**", "/api/auth/login", "/api/auth/forgot-password",
                        "/api/auth/reset-password", "/api/auth/reset-password/validate");
    }
}
