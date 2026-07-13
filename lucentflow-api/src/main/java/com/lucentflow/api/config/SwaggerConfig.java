package com.lucentflow.api.config;

import com.lucentflow.api.config.ConditionalOnApiEnabled;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Spring Boot configuration for OpenAPI 3.0 documentation and Swagger UI.
 * 
 * <p>Implementation Details:
 * Configures comprehensive API documentation with custom metadata, licensing,
 * and server environments. Provides interactive API exploration through Swagger UI.
 * Virtual thread compatible through immutable configuration beans and stateless design.
 * Ensures consistent API documentation across development and production environments.
 * </p>
 * 
 * @author ArchLucent
 * @since 1.0
 */
@ConditionalOnApiEnabled
@Configuration
public class SwaggerConfig {
    
    /**
     * Creates and configures the OpenAPI 3.0 specification bean.
     * 
     * <p>Configures API metadata including title, description, version, contact information,
     * licensing, and server environments. Provides comprehensive documentation for
     * whale transaction querying and blockchain synchronization endpoints.</p>
     * 
     * @return Configured OpenAPI specification for Swagger UI generation
     */
    @Bean
    public OpenAPI lucentFlowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LucentFlow API")
                        .description("Forensics, watchlist, alert-rules, and usage APIs for project-scoped Base monitoring. "
                                + "Public endpoints: /api/v1/whales, /api/v1/sync-status, /api/v1/whales/stats. "
                                + "Project endpoints require X-Project-Key. "
                                + "Admin project management requires X-Admin-Key (LUCENTFLOW_ADMIN_API_KEY).")
                        .version("1.2.0-STABLE")
                        .contact(new Contact()
                                .name("LucentFlow Team")
                                .email("info@lucentflow.io")
                                .url("https://lucentflow.io"))
                        .license(new License()
                                .name("Apache License 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(new Components().addSecuritySchemes("projectKey",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Project-Key")
                                .description("Project API key used for multi-project scope isolation."))
                        .addSecuritySchemes("adminKey",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Admin-Key")
                                        .description("Admin API key (LUCENTFLOW_ADMIN_API_KEY). Returns 503 when unset.")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.lucentflow.io")
                                .description("Production Server")
                ));
    }
}
