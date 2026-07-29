package com.bobfull.payment.config;

import io.portone.sdk.server.PortOneClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PortOneProperties.class)
public class PortOneConfig {
    @Bean
    public PortOneClient portOneClient(PortOneProperties properties) {
        return new PortOneClient(properties.apiSecret(), "https://api.portone.io", properties.storeId());
    }
}
