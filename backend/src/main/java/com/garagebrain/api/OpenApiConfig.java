package com.garagebrain.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI garageBrainOpenApi(@Value("${server.port:8080}") int serverPort) {
        return new OpenAPI()
                .info(new Info()
                        .title("Garage Brain API")
                        .description(
                                "Open-source predictive OBD analytics. Import Car Scanner CSV exports, "
                                        + "build per-vehicle baselines, and surface early trend alerts.")
                        .version("v1")
                        .contact(new Contact()
                                .name("Garage Brain")
                                .url("https://github.com/arieznikov/garage-brain"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addServersItem(new Server().url("http://localhost:" + serverPort).description("Local dev"));
    }
}
