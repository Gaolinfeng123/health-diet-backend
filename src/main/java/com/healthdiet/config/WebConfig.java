package com.healthdiet.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File; // 导入这个

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Value("${file.upload-path}")
    private String uploadPath;
    @Value("${file.access-path}")
    private String accessPath;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/user/login", "/api/user/register")
                .excludePathPatterns("/api/auth/**")
                .excludePathPatterns("/doc.html", "/webjars/**", "/v3/**", "/favicon.ico")
                .excludePathPatterns("/images/**");
    }

    // --- 核心修改：动态映射路径 ---
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 获取项目根目录
        String projectPath = System.getProperty("user.dir");

        // 拼凑出绝对路径：file:C:/.../backend/diet-uploads/
        // File.separator 会自动识别是 Windows(\) 还是 Mac(/)
        String location = "file:" + projectPath + File.separator + uploadPath;

        registry.addResourceHandler(accessPath + "**")
                .addResourceLocations(location);
    }
}