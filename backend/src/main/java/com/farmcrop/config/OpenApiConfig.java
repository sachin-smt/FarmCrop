package com.farmcrop.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata. The UI is enabled only in the dev profile (see application-*.properties). */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI farmCropOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FarmCrop API")
                        .version("v1")
                        .description("Agricultural marketplace API for buyers, farmers (sellers) and administrators."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
