package com.kaayakasetu.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * The (unchanged) frontend references /static/css/style.css and /static/js/app.js.
     * Spring Boot normally serves classpath:/static/ at the ROOT (/css/style.css), so we add
     * a second mapping to keep the exact URLs the HTML already uses.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");
    }

    /** Same as the FastAPI CORSMiddleware(allow_origins=["*"]). Fine for a demo; restrict in production. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
