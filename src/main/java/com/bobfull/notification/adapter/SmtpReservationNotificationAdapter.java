package com.bobfull.notification.adapter;

import com.bobfull.reservation.port.ReservationNotificationPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
                "예약이 확정됐어요", "#2f9e44", "즐거운 식사 되세요!");
    }

    @Override
    public void notifyCancelledDueToInsufficientParticipants(ReservationResultNotification notification) {
        send(notification, "CANCELLED", "[밥풀] 예약이 취소되었습니다",
                "예약이 취소됐어요", "#e03131", "최소 인원 미달로 취소되었습니다. 결제 금액은 환불 절차가 진행됩니다.");
    }

    private void send(
            ReservationResultNotification notification, String result, String subject,
            String title, String accentColor, String message
    ) {
        String mealTime = MEAL_TIME_FORMAT.format(notification.mealStartAt());
        String restaurantName = escapeHtml(notification.restaurantName());
        String htmlBody = buildHtmlBody(title, accentColor, restaurantName, mealTime, message);
        String textBody = "%s\n식당: %s\n식사 일시: %s\n%s".formatted(
                title, notification.restaurantName(), mealTime, message);

        for (Recipient recipient : notification.recipients()) {
            sendToRecipient(notification.reservationId(), recipient, result, subject, htmlBody, textBody);
        }
    }

    private void sendToRecipient(
            Long reservationId, Recipient recipient, String result, String subject, String htmlBody, String textBody
    ) {
        MimeMessage message = buildMessage(recipient.email(), subject, htmlBody, textBody);
        if (message == null) {
            log.error("event=RESERVATION_NOTIFICATION_FAILED reservationId={} memberId={} result={} reason=MESSAGE_BUILD_FAILED",
                    reservationId, recipient.memberId(), result);
            return;
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                mailSender.send(message);
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

    private MimeMessage buildMessage(String to, String subject, String htmlBody, String textBody) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);
            return message;
        } catch (MessagingException exception) {
            return null;
        }
    }

    private String buildHtmlBody(String title, String accentColor, String restaurantName, String mealTime, String message) {
        return """
                <div style="font-family:-apple-system,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;max-width:420px;margin:0 auto;padding:32px 24px;border:1px solid #eee;border-radius:16px;">
                  <p style="color:#999;font-size:13px;margin:0 0 4px;">밥풀</p>
                  <h2 style="color:%s;margin:0 0 20px;font-size:20px;">%s</h2>
                  <table style="width:100%%;border-collapse:collapse;margin-bottom:20px;">
                    <tr>
                      <td style="padding:8px 0;color:#888;font-size:14px;">식당</td>
                      <td style="padding:8px 0;text-align:right;font-weight:600;font-size:14px;">%s</td>
                    </tr>
                    <tr>
                      <td style="padding:8px 0;color:#888;font-size:14px;">식사 일시</td>
                      <td style="padding:8px 0;text-align:right;font-weight:600;font-size:14px;">%s</td>
                    </tr>
                  </table>
                  <p style="color:#555;font-size:14px;line-height:1.6;margin:0;">%s</p>
                </div>
                """.formatted(accentColor, title, restaurantName, mealTime, message);
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
