package com.bobfull.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

class RedisConnectionConfigurationTest {
    private final ApplicationContextRunner redisAutoConfigRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class));

    @Test
    void prod_profile_enables_lettuce_ssl_by_default() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "REDIS_HOST=example.cache.amazonaws.com")
                .run(context -> {
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);

                    assertThat(factory.isUseSsl()).isTrue();
                });
    }

    @Test
    void default_redis_auto_configuration_keeps_lettuce_ssl_disabled() {
        redisAutoConfigRunner
                .withPropertyValues("spring.data.redis.host=localhost")
                .run(context -> {
                    LettuceConnectionFactory factory = context.getBean(LettuceConnectionFactory.class);

                    assertThat(factory.isUseSsl()).isFalse();
                });
    }
}
