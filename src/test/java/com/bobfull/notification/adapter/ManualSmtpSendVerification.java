package com.bobfull.notification.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bobfull.reservation.port.ReservationNotificationPort.Recipient;
import com.bobfull.reservation.port.ReservationNotificationPort.ReservationResultNotification;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 Gmail SMTP로 확정 안내 테스트 메일 한 통을 보내 로컬 메일 설정을 수동으로 검증한다
 * (Issue #168 직접 검증). 평소에는 항상 비활성화돼 있고 CI·전체 테스트에서 절대 실행되지 않는다.
 *
 * 실행 방법:
 * 1. {@code application-local.yml}, {@code .env}에 실제 Gmail SMTP 정보(spring.mail.*,
 *    notification.email.from-address)를 채운다.
 * 2. 로컬 DB·Redis를 평소 앱을 실행할 때처럼 띄운다(전체 Spring Context를 올리기 때문).
 * 3. 아래 {@code @Disabled} 줄을 잠시 지운 뒤 이 클래스 하나만 단독 실행한다.
 *    예: ./gradlew :test --tests "com.bobfull.notification.adapter.ManualSmtpSendVerification"
 *        -Dspring.profiles.active=local
 * 4. 본인 Gmail 수신함에서 실제 도착을 직접 확인한 뒤, {@code @Disabled}를 다시 복원한다.
 */
@Disabled("실제 Gmail 발송을 수동으로 확인할 때만 이 줄을 지우고 단독 실행한다")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
class ManualSmtpSendVerification {

    @Value("${spring.mail.username}")
    private String testRecipient;

    @Value("${notification.email.from-address}")
    private String fromAddress;

    @Autowired
    private JavaMailSender mailSender;

    @Test
    void 본인_Gmail로_확정_안내_테스트_메일을_보낸다() {
        SmtpReservationNotificationAdapter adapter = new SmtpReservationNotificationAdapter(mailSender, fromAddress);
        ReservationResultNotification notification = new ReservationResultNotification(
                0L, "수동 검증 식당", Instant.now(), List.of(new Recipient(0L, testRecipient, "수동 검증")));

        adapter.notifyConfirmed(notification);

        // 실제 도착 여부는 자동 검증할 수 없다 — 본인 Gmail 수신함에서 직접 확인한다.
        assertThat(testRecipient).isNotBlank();
    }
}
