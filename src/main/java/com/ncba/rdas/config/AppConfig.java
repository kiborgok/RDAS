package com.ncba.rdas.config;

import com.ncba.rdas.config.RdasProperties.Soap;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Core beans: configuration-properties activation and the {@link RestTemplate}
 * used by the SOAP client (configured with sensible connect/read timeouts so a
 * slow upstream cannot exhaust request threads).
 */
@Configuration
@EnableConfigurationProperties(RdasProperties.class)
public class AppConfig {

    @Bean
    public RestTemplate soapRestTemplate(RestTemplateBuilder builder, RdasProperties props) {
        Soap soap = props.getSoap();
        return builder
                .setConnectTimeout(Duration.ofMillis(soap.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(soap.getReadTimeoutMs()))
                .build();
    }
}
