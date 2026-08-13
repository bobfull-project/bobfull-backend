package com.bobfull.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

/**
 * #192 {@code bobfull.kafka.chat-message.consumer-concurrency} 설정이 실제
 * {@link ConcurrentMessageListenerContainer}에 반영되는지만 검증한다. Consumer 1→2→3 실측 처리량 비교는
 * 후속 실험 PR 범위다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat-moderation-consumer-concurrency-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=chat-moderation-consumer-concurrency-test-secret-key-please-keep-long",
        "jwt.access-token-expiration-seconds=1800",
        "portone.api-secret=chat-moderation-consumer-concurrency-test-api-secret",
        "portone.store-id=chat-moderation-consumer-concurrency-test-store-id",
        "portone.webhook-secret=Y2hhdC1tb2RlcmF0aW9uLWNvbmN1cnJlbmN5LXRlc3Q=",
        "outbox.chat-message.enabled=false",
        "bobfull.kafka.chat-message.consumer-enabled=true",
        "bobfull.kafka.chat-message.topic-auto-create-enabled=true",
        "bobfull.kafka.chat-message.topic=chat-moderation-concurrency-it.v1",
        "bobfull.kafka.chat-message.dlt-topic=chat-moderation-concurrency-it.dlt.v1",
        "bobfull.kafka.chat-message.consumer-concurrency=3"
})
class ChatModerationConsumerConcurrencyIntegrationTest {

    @Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.7.1");

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Test
    void consumer_concurrency_설정이_리스너_컨테이너에_반영된다() {
        Collection<MessageListenerContainer> containers = registry.getListenerContainers();

        assertThat(containers).hasSize(1);
        MessageListenerContainer container = containers.iterator().next();
        assertThat(container).isInstanceOf(ConcurrentMessageListenerContainer.class);
        assertThat(((ConcurrentMessageListenerContainer<?, ?>) container).getConcurrency()).isEqualTo(3);
    }
}
