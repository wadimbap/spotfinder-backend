package com.spotfinder.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI spotfinderOpenApi() {
        return new OpenAPI()
                .info(new Info()
                .title("SpotFinder backend API")
                        .description("API documentation for SpotFinder backend")
                        .version("v1"));
    }
}
