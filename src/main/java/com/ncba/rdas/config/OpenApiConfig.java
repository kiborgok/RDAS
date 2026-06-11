package com.ncba.rdas.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rdasOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Reference Data Aggregation Service (RDAS) API")
                .description("Single source of truth for country reference data. "
                        + "Aggregates the CountryInfo SOAP service behind a consistent, "
                        + "cached, paginated and filterable REST/JSON API.")
                .version("1.0.0")
                .contact(new Contact().name("LOOP DFS - Digital Business").email("digital@loop.co.ke"))
                .license(new License().name("Internal use")));
    }
}
