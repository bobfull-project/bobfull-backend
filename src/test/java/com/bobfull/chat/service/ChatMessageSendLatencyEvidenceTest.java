package com.bobfull.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bobfull.chat.entity.ChatRoom;
import com.bobfull.chat.repository.ChatRoomRepository;
import com.bobfull.chat.port.ReservationChatAccessReader;
import com.bobfull.common.security.AuthMember;
import com.bobfull.common.security.MemberRole;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.ReservationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * #59 Evidence — MAJOR 1 수정(signal 비동기화) 이후 Chat SEND 경로가 Kafka 상태와
 * 무관하게 빠르게 반환되는지 실측한다. 로컬 broker 없이 실행되므로(Kafka 미도달),
 * signal 디스패치가 여전히 send() 호출자를 막지 않음을 함께 증명한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat-send-latency-evidence;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=chat-send-latency-evidence-secret-key-please-keep-long",
        "jwt.access-token-expiration-seconds=1800",
        "portone.api-secret=chat-send-latency-evidence-api-secret",
        "portone.store-id=chat-send-latency-evidence-store-id",
        "portone.webhook-secret=Y2hhdC1zZW5kLWxhdGVuY3ktZXZpZGVuY2U=",
        "outbox.chat-message.enabled=false",
        "bobfull.kafka.chat-message.consumer-enabled=false",
        "spring.kafka.bootstrap-servers=localhost:59999"
})
@ContextConfiguration(classes = ChatMessageSendLatencyEvidenceTest.Configuration.class)
class ChatMessageSendLatencyEvidenceTest {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageSendLatencyEvidenceTest.class);
    private static final int SAMPLE_SIZE = 50;

    @Autowired private ChatMessageCommandService service;
    @Autowired private ChatRoomRepository chatRoomRepository;

    @Test void Kafka에_도달하지_못하는_상태에서도_send는_빠르게_반환된다() {
        ChatRoom room = chatRoomRepository.saveAndFlush(ChatRoom.create(1L));
        AuthMember member = new AuthMember(1L, MemberRole.MEMBER);

        List<Long> elapsedMillis = new ArrayList<>(SAMPLE_SIZE);
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            long startedAt = System.nanoTime();
            service.send(room.getId(), member, "지연시간 측정용 메시지 " + i);
            elapsedMillis.add((System.nanoTime() - startedAt) / 1_000_000);
        }

        List<Long> sorted = elapsedMillis.stream().sorted().toList();
        long p50 = sorted.get((int) (SAMPLE_SIZE * 0.50));
        long p95 = sorted.get((int) (SAMPLE_SIZE * 0.95) == SAMPLE_SIZE ? SAMPLE_SIZE - 1 : (int) (SAMPLE_SIZE * 0.95));
        long max = sorted.get(SAMPLE_SIZE - 1);
        log.info("event=CHAT_SEND_LATENCY_EVIDENCE sampleSize={} p50Millis={} p95Millis={} maxMillis={}",
                SAMPLE_SIZE, p50, p95, max);

        // Kafka(localhost:59999)에 실제로 도달할 수 없는 상태에서도 send()는 로컬 DB 작업 수준으로 빠르게 반환돼야 한다.
        assertThat(p95).isLessThan(500);
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
