package com.agent.hopaw.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ThemeInterceptor themeInterceptor;
    private final AuthInterceptor authInterceptor;
    private final IpBlacklistInterceptor ipBlacklistInterceptor;

    @Value("${hopaw.attachment.dir:./attachments}")
    private String attachmentDir;

    @Value("${hopaw.attachment.url-prefix:/attachments}")
    private String attachmentUrlPrefix;

    public WebMvcConfig(ThemeInterceptor themeInterceptor, AuthInterceptor authInterceptor,
                        IpBlacklistInterceptor ipBlacklistInterceptor) {
        this.themeInterceptor = themeInterceptor;
        this.authInterceptor = authInterceptor;
        this.ipBlacklistInterceptor = ipBlacklistInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 导出文件下载：将 /exports/** 映射到项目根目录下的 exports/ 文件夹
        String exportPath = "file:" + System.getProperty("user.dir") + "/exports/";
        registry.addResourceHandler("/exports/**")
                .addResourceLocations(exportPath);
        String tempFilePath = "file:" + System.getProperty("user.dir") + "/tempFilePath/";
        registry.addResourceHandler("/tempFile/**")
                .addResourceLocations(tempFilePath);
        // 附件文件访问：将 /attachments/** 映射到配置的附件目录
        File dir = new File(attachmentDir);
        if (!dir.isAbsolute()) {
            dir = new File(System.getProperty("user.dir"), attachmentDir);
        }
        String attachmentPath = "file:" + dir.getAbsolutePath() + "/";
        registry.addResourceHandler(attachmentUrlPrefix + "/**")
                .addResourceLocations(attachmentPath);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // IP黑名单拦截器（最高优先级）
        registry.addInterceptor(ipBlacklistInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**", "/css/**", "/js/**", "/icons/**", "/images/**");

        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**", "/css/**", "/js/**", "/icons/**", "/images/**", "/test/**", "/ws/**", "/error", "/exports/**", attachmentUrlPrefix + "/**");

        registry.addInterceptor(themeInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/static/**", "/css/**", "/js/**", "/icons/**", "/images/**", "/test/**", "/exports/**");
    }
}
