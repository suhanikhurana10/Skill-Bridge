package com.kaayakasetu.common;

import com.kaayakasetu.config.AppConstants;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** "/" (the dashboard page) and "/health" - equivalent of the two routes in app/main.py. */
@RestController
public class HealthController {

    @Hidden
    @GetMapping("/")
    public ResponseEntity<Resource> dashboard() {
        Resource index = new ClassPathResource("static/index.html");
        if (!index.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(index);
    }

    @Tag(name = "System")
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> services = new LinkedHashMap<>();
        services.put("workers", "UP");
        services.put("jobs", "UP");
        services.put("matching", "UP");
        services.put("feeds", "UP");
        services.put("location", "UP");
        services.put("wage_prediction", "UP");
        services.put("contact", "UP");
        services.put("ivr_ussd_telecom", "UP");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "healthy");
        body.put("version", AppConstants.APP_VERSION);
        body.put("services", services);
        return body;
    }
}
