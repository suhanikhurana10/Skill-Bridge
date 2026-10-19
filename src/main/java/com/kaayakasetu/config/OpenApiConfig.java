package com.kaayakasetu.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title/description shown on the Swagger page (/docs). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI kaayakasetuOpenApi() {
        // adds the "Authorize" button to Swagger UI: log in via /api/v1/auth/login, paste the access_token there
        return new OpenAPI()
                .info(new Info()
                        .title(AppConstants.APP_TITLE)
                        .description(AppConstants.APP_DESCRIPTION)
                        .version(AppConstants.APP_VERSION))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
