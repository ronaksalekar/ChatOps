package com.chatops.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc / OpenAPI 3 configuration.
 *
 * <p>Registers a Bearer JWT security scheme globally so every protected endpoint
 * in the Swagger UI shows a padlock and allows the user to supply a token once
 * rather than per-request.
 *
 * <p>Access the UI at {@code /swagger-ui.html} (dev profile only).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI chatOpsOpenApi() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local Dev"),
                        new Server().url("https://your-domain.com").description("Production")
                ))
                // Declare the JWT Bearer security scheme
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste your JWT access token here (without 'Bearer ' prefix).")))
                // Apply globally — every endpoint requires auth unless overridden
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
    }

    private Info apiInfo() {
        return new Info()
                .title("ChatOps API")
                .version("1.0.0")
                .description("""
                        Real-time group chat backend.

                        **Authentication flow:**
                        1. `POST /api/auth/register` — create an account
                        2. `POST /api/auth/login` — receive `accessToken` + `refreshToken`
                        3. Click **Authorize** above and paste the `accessToken`
                        4. Use `POST /api/auth/refresh` to rotate tokens before expiry
                        """)
                .contact(new Contact()
                        .name("ChatOps Team")
                        .email("hello@chatops.dev"))
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT"));
    }
}
