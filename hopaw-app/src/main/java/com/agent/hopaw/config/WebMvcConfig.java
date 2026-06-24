package com.agent.hopaw.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
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
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 导出文件下载：将 /exports/** 映射到项目根目录下的 exports/ 文件夹
        String exportPath = "file:" + System.getProperty("user.dir") + "/exports/";
        registry.addResourceHandler("/exports/**")
                .addResourceLocations(exportPath);
        // 上传文件访问：将 /uploads/** 映射到项目根目录下的 uploads/ 文件夹
        String uploadPath = "file:" + System.getProperty("user.dir") + "/uploads/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**", "/css/**", "/js/**", "/icons/**", "/images/**", "/test/**", "/ws/**", "/error", "/exports/**");

        registry.addInterceptor(themeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**", "/css/**", "/js/**", "/icons/**", "/images/**", "/test/**", "/exports/**");
    }
}
