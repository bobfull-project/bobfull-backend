package com.bobfull.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.bobfull.chat.entity.ChatRoom;
import com.bobfull.chat.port.ReservationChatAccessReader;
import com.bobfull.chat.repository.ChatModerationRepository;
import com.bobfull.chat.repository.ChatRoomRepository;
import com.bobfull.chat.service.ChatMessageCommandService;
import com.bobfull.common.security.AuthMember;
import com.bobfull.common.security.MemberRole;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.ReservationStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

/**
 * #192 {@code bobfull.kafka.chat-message.consumer-concurrency} 설정이 실제
 * {@link ConcurrentMessageListenerContainer}에 반영되는지 검증하고, 같은 컨텍스트에서
 * "Kafka vs Async Baseline" 비교의 Kafka 축(send()→Outbox→Kafka→Consumer 실측)도 측정한다.
 * Async 축은 {@code ChatMessageAsyncModerationBaselineEvidenceTest}에서 같은 Fake AI 지연·메시지 수로 측정한다.
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
        "bobfull.kafka.chat-message.consumer-concurrency=3",
        "bobfull.ai.moderation.fake-enabled=true",
        "bobfull.ai.moderation.fake-latency-ms=500"
})
@ContextConfiguration(classes = ChatModerationConsumerConcurrencyIntegrationTest.Configuration.class)
class ChatModerationConsumerConcurrencyIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ChatModerationConsumerConcurrencyIntegrationTest.class);
    private static final int SAMPLE_SIZE = 30;
    private static final long FAKE_AI_LATENCY_MILLIS = 500;
    private static final int CONCURRENCY = 3;

    @Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.7.1");

    @Autowired
    private KafkaListenerEndpointRegistry registry;
    @Autowired
    private ChatMessageCommandService service;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatModerationRepository chatModerationRepository;

    @Test
    void consumer_concurrency_설정이_리스너_컨테이너에_반영된다() {
        Collection<MessageListenerContainer> containers = registry.getListenerContainers();

        assertThat(containers).hasSize(1);
        MessageListenerContainer container = containers.iterator().next();
        assertThat(container).isInstanceOf(ConcurrentMessageListenerContainer.class);
        assertThat(((ConcurrentMessageListenerContainer<?, ?>) container).getConcurrency()).isEqualTo(3);
    }

    @Test
    void Kafka_경로는_같은_AI_지연에서도_send는_빠르고_Consumer_동시성만큼_완료된다() {
        ChatRoom room = chatRoomRepository.saveAndFlush(ChatRoom.create(1L));
        AuthMember member = new AuthMember(1L, MemberRole.MEMBER);
        long baselineCompleted = chatModerationRepository.count();

        List<Long> sendElapsedMillis = new ArrayList<>(SAMPLE_SIZE);
        Instant startedAt = Instant.now();
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            long start = System.nanoTime();
            service.send(room.getId(), member, "kafka arm 측정용 메시지 " + i);
            sendElapsedMillis.add((System.nanoTime() - start) / 1_000_000);
        }

        List<Long> sorted = sendElapsedMillis.stream().sorted().toList();
        long p50 = sorted.get((int) (SAMPLE_SIZE * 0.50));
        long p95 = sorted.get((int) (SAMPLE_SIZE * 0.95) == SAMPLE_SIZE ? SAMPLE_SIZE - 1 : (int) (SAMPLE_SIZE * 0.95));
        long max = sorted.get(SAMPLE_SIZE - 1);

        long expectedTotal = baselineCompleted + SAMPLE_SIZE;
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(chatModerationRepository.count()).isEqualTo(expectedTotal));
        long drainMillis = Duration.between(startedAt, Instant.now()).toMillis();

        log.info("event=KAFKA_ARM_EVIDENCE sampleSize={} fakeAiLatencyMillis={} consumerConcurrency={} "
                        + "sendP50Millis={} sendP95Millis={} sendMaxMillis={} drainMillis={}",
                SAMPLE_SIZE, FAKE_AI_LATENCY_MILLIS, CONCURRENCY, p50, p95, max, drainMillis);

        // Kafka 경로도 send()는 Outbox 저장만 동기로 하고 Kafka 발행·AI 호출은 커밋 후 비동기라 빠르게 반환된다.
        assertThat(p95).isLessThan(500);
    }

    @Test
    void 같은_채팅방에_몰리면_Partition_key가_같아_Consumer_3개여도_한_Consumer만_처리한다() {
        ChatRoom room = chatRoomRepository.saveAndFlush(ChatRoom.create(2L));
        AuthMember member = new AuthMember(1L, MemberRole.MEMBER);
        long baselineCompleted = chatModerationRepository.count();

        Instant startedAt = Instant.now();
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            service.send(room.getId(), member, "단일 채팅방 몰림 측정용 메시지 " + i);
        }

        long expectedTotal = baselineCompleted + SAMPLE_SIZE;
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(chatModerationRepository.count()).isEqualTo(expectedTotal));
        long drainMillis = Duration.between(startedAt, Instant.now()).toMillis();

        log.info("event=KAFKA_ARM_SINGLE_ROOM_KEY_EVIDENCE sampleSize={} fakeAiLatencyMillis={} consumerConcurrency={} drainMillis={}",
                SAMPLE_SIZE, FAKE_AI_LATENCY_MILLIS, CONCURRENCY, drainMillis);

        // chatRoomId가 Partition key이므로 같은 방 메시지는 항상 같은 Partition에 몰려 Consumer 1개만 처리한다.
        // 이론적 순차 처리 시간(지연×N)에 근접해야 한다(오차 허용을 위해 90%로 완화).
        assertThat(drainMillis).isGreaterThanOrEqualTo((long) (FAKE_AI_LATENCY_MILLIS * SAMPLE_SIZE * 0.9));
    }

    @Test
    void 채팅방을_분산하면_Partition도_분산돼_Consumer_동시성만큼_처리량이_늘어난다() {
        AuthMember member = new AuthMember(1L, MemberRole.MEMBER);
        List<ChatRoom> rooms = new ArrayList<>();
        for (int i = 0; i < CONCURRENCY; i++) {
            rooms.add(chatRoomRepository.saveAndFlush(ChatRoom.create(100L + i)));
        }
        long baselineCompleted = chatModerationRepository.count();

        Instant startedAt = Instant.now();
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            ChatRoom room = rooms.get(i % CONCURRENCY);
            service.send(room.getId(), member, "채팅방 분산 측정용 메시지 " + i);
        }

        long expectedTotal = baselineCompleted + SAMPLE_SIZE;
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(chatModerationRepository.count()).isEqualTo(expectedTotal));
        long drainMillis = Duration.between(startedAt, Instant.now()).toMillis();

        log.info("event=KAFKA_ARM_MULTI_ROOM_KEY_EVIDENCE sampleSize={} fakeAiLatencyMillis={} consumerConcurrency={} roomCount={} drainMillis={}",
                SAMPLE_SIZE, FAKE_AI_LATENCY_MILLIS, CONCURRENCY, CONCURRENCY, drainMillis);

        // Partition key(chatRoomId)를 몇 개의 방으로 나누면 병렬 처리가 일어나 순차 처리 시간보다는
        // 확실히 빨라진다. 다만 room 3개의 key 해시가 Partition 3개에 반드시 고르게 흩어지는 것은
        // 아니라서(해시 충돌 가능) 이론적 3배 단축을 강제하지 않고 여유 있게 85% 미만으로만 검증한다.
        assertThat(drainMillis).isLessThan((long) (FAKE_AI_LATENCY_MILLIS * SAMPLE_SIZE * 0.85));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean @Primary
        ReservationChatAccessReader fixedActiveAccessReader() {
            return (reservationId, memberId) -> new ReservationChatAccessReader.ChatAccess(
                    100L, ParticipationStatus.RESERVED, ReservationStatus.RECRUITING);
        }
    }
}
