package com.kaayakasetu.common;

import com.kaayakasetu.config.AppConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Prints the friendly "where do I click" banner that run.py printed. ASCII only so Windows consoles never garble it. */
@Component
public class StartupBanner implements ApplicationRunner {

    private final String host;
    private final String port;

    public StartupBanner(@Value("${server.address:127.0.0.1}") String host,
                         @Value("${server.port:8000}") String port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void run(ApplicationArguments args) {
        String base = "http://" + ("0.0.0.0".equals(host) ? "localhost" : host) + ":" + port;
        System.out.println();
        System.out.println("================================================================================");
        System.out.println("  " + AppConstants.APP_TITLE);
        System.out.println("================================================================================");
        System.out.println("  Platform Status:    Online");
        System.out.println("  Web Dashboard:      " + base + "/");
        System.out.println("  API Docs (Swagger): " + base + "/docs");
        System.out.println("  OpenAPI JSON:       " + base + "/openapi.json");
        System.out.println("  Health Check:       " + base + "/health");
        System.out.println();
        System.out.println("Modules loaded:");
        System.out.println("  * Workers            -> /api/v1/workers");
        System.out.println("  * Jobs               -> /api/v1/jobs");
        System.out.println("  * Smart Matching     -> /api/v1/match");
        System.out.println("  * Feeds & Insights   -> /api/v1/feeds");
        System.out.println("  * Location           -> /api/v1/location");
        System.out.println("  * Fair Wage          -> /api/v1/wage");
        System.out.println("  * Contact & SMS      -> /api/v1/contact");
        System.out.println("  * USSD & IVR Gateway -> /api/v1/telecom  (Kannada, Hindi, English)");
        System.out.println("================================================================================");
    }
}
