package com.lucentflow.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
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
                        .description("API for querying whale transactions and blockchain synchronization status")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("LucentFlow Team")
                                .email("info@lucentflow.io")
                                .url("https://lucentflow.io"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
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
