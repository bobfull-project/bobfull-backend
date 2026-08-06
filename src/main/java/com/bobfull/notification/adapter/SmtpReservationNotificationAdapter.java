package com.bobfull.notification.adapter;

import com.bobfull.reservation.port.ReservationNotificationPort;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 실제 SMTP 서버로 예약 확정·인원 미달 취소 안내 메일을 발송한다(Issue #168).
 * 이 클래스가 호출되는 시점에는 예약 상태 전이 트랜잭션이 이미 커밋돼 있으므로, 재시도로
 * 시간이 걸려도 DB 락이나 요청 스레드를 점유하지 않는다. 참여자별로 서로 독립적으로 최대
 * {@value #MAX_ATTEMPTS}회까지 재시도하며, 한 명의 발송 실패가 다른 참여자의 발송을 막지
 * 않는다. 재시도를 모두 소진해도 예외를 던지지 않고 실패만 로그로 남긴다 — 이메일 주소·본문은
 * 로그에 남기지 않는다.
 */
@Component
public class SmtpReservationNotificationAdapter implements ReservationNotificationPort {
    private static final Logger log = LoggerFactory.getLogger(SmtpReservationNotificationAdapter.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final DateTimeFormatter MEAL_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.KOREAN).withZone(ZoneId.of("Asia/Seoul"));

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpReservationNotificationAdapter(
            JavaMailSender mailSender,
            @Value("${notification.email.from-address:no-reply@bobfull.com}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void notifyConfirmed(ReservationResultNotification notification) {
        send(notification, "CONFIRMED", "[밥풀] 예약이 확정되었습니다",
                "%s에서 %s 식사 예약이 확정되었습니다. 즐거운 식사 되세요!");
    }

    @Override
    public void notifyCancelledDueToInsufficientParticipants(ReservationResultNotification notification) {
        send(notification, "CANCELLED", "[밥풀] 예약이 취소되었습니다",
                "%s의 %s 식사 예약이 최소 인원 미달로 취소되었습니다. 결제 금액은 환불 절차가 진행됩니다.");
    }

    private void send(ReservationResultNotification notification, String result, String subject, String bodyTemplate) {
        String mealTime = MEAL_TIME_FORMAT.format(notification.mealStartAt());
        String body = bodyTemplate.formatted(notification.restaurantName(), mealTime);
        for (Recipient recipient : notification.recipients()) {
            sendToRecipient(notification.reservationId(), recipient, result, subject, body);
        }
    }

    private void sendToRecipient(Long reservationId, Recipient recipient, String result, String subject, String body) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                mailSender.send(buildMessage(recipient.email(), subject, body));
                log.info("event=RESERVATION_NOTIFICATION_SENT reservationId={} memberId={} result={} attempt={}",
                        reservationId, recipient.memberId(), result, attempt);
                return;
            } catch (MailException exception) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("event=RESERVATION_NOTIFICATION_FAILED reservationId={} memberId={} result={} attempts={}",
                            reservationId, recipient.memberId(), result, MAX_ATTEMPTS);
                } else {
                    log.warn("event=RESERVATION_NOTIFICATION_ATTEMPT_FAILED reservationId={} memberId={} result={} attempt={}",
                            reservationId, recipient.memberId(), result, attempt);
                }
            }
        }
    }

    private SimpleMailMessage buildMessage(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        return message;
    }
}
