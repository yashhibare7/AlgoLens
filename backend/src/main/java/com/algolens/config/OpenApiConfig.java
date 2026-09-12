package com.algolens.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI algoLensOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AlgoLens API")
                        .version("v0.1.0")
                        .description("""
                                Step-by-step DSA code visualizer.

                                Submit source code to `POST /api/executions` and get back an
                                `ExecutionTrace`: one event per executed statement, each carrying
                                the source line, scalar variables, highlighted data structures and
                                a human readable message. The trace format is language independent
                                by design, so the same frontend renders every executor.

                                Send `Authorization: Bearer <token>` from `POST /api/auth/login`.
                                """)
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
