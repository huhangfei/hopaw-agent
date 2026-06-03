package com.agent.hopaw.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ThemeInterceptor themeInterceptor;
    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(ThemeInterceptor themeInterceptor, AuthInterceptor authInterceptor) {
        this.themeInterceptor = themeInterceptor;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**", "/css/**", "/js/**", "/icons/**", "/images/**", "/test/**", "/ws/**", "/error");

        registry.addInterceptor(themeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**", "/css/**", "/js/**", "/icons/**", "/images/**", "/test/**");
    }
}
