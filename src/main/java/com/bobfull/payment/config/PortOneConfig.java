package com.bobfull.payment.config;

import io.portone.sdk.server.PortOneClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.portone.sdk.server.webhook.WebhookVerifier;

@Configuration
@EnableConfigurationProperties(PortOneProperties.class)
public class PortOneConfig {
    @Bean
    public PortOneClient portOneClient(PortOneProperties properties) {
        return new PortOneClient(properties.apiSecret(), "https://api.portone.io", properties.storeId());
    }
    @Bean
    public WebhookVerifier webhookVerifier(PortOneProperties properties) { return new WebhookVerifier(properties.webhookSecret()); }
}
