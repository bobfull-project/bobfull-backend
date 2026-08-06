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
import org.springframework.core.io.ClassPathResource;
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
 *
 * <p>메일 본문의 제목·문구는 {@code mail/images/reservation-confirmed.png},
 * {@code reservation-cancelled.png} 일러스트에 이미 포함돼 있어, HTML에서 별도로 반복하지
 * 않는다 — 동적 정보(식당명·주소·일시)만 이미지 아래 정보 카드에 채운다.</p>
 */
@Component
public class SmtpReservationNotificationAdapter implements ReservationNotificationPort {
    private static final Logger log = LoggerFactory.getLogger(SmtpReservationNotificationAdapter.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MEAL_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy. MM. dd (E)", Locale.KOREAN).withZone(SEOUL_ZONE);
    private static final DateTimeFormatter MEAL_TIME_FORMAT =
            DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN).withZone(SEOUL_ZONE);

    private static final String LOGO_CID = "bobfullLogo";
    private static final String FOOTER_CID = "emailFooter";
    private static final String CONFIRMED_CID = "reservationConfirmed";
    private static final String CANCELLED_CID = "reservationCancelled";
    private static final String LOGO_PATH = "mail/images/bobfull-logo.png";
    private static final String FOOTER_PATH = "mail/images/email-footer-background.png";
    private static final String CONFIRMED_IMAGE_PATH = "mail/images/reservation-confirmed.png";
    private static final String CANCELLED_IMAGE_PATH = "mail/images/reservation-cancelled.png";

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
        send(notification, "CONFIRMED", "[밥풀] 예약이 확정되었습니다", CONFIRMED_CID, CONFIRMED_IMAGE_PATH);
    }

    @Override
    public void notifyCancelledDueToInsufficientParticipants(ReservationResultNotification notification) {
        send(notification, "CANCELLED", "[밥풀] 예약이 취소되었습니다", CANCELLED_CID, CANCELLED_IMAGE_PATH);
    }

    private void send(
            ReservationResultNotification notification, String result, String subject,
            String resultImageCid, String resultImagePath
    ) {
        String mealDate = MEAL_DATE_FORMAT.format(notification.mealStartAt());
        String mealTime = MEAL_TIME_FORMAT.format(notification.mealStartAt());
        String restaurantName = escapeHtml(notification.restaurantName());
        String restaurantAddress = escapeHtml(notification.restaurantAddress());
        String htmlBody = buildHtmlBody(resultImageCid, restaurantName, mealDate, mealTime, restaurantAddress);
        String textBody = "식당: %s\n주소: %s\n예약 날짜: %s\n식사 시작 시간: %s".formatted(
                notification.restaurantName(), notification.restaurantAddress(), mealDate, mealTime);

        for (Recipient recipient : notification.recipients()) {
            sendToRecipient(notification.reservationId(), recipient, result, subject, htmlBody, textBody, resultImageCid, resultImagePath);
        }
    }

    private void sendToRecipient(
            Long reservationId, Recipient recipient, String result, String subject,
            String htmlBody, String textBody, String resultImageCid, String resultImagePath
    ) {
        MimeMessage message = buildMessage(recipient.email(), subject, htmlBody, textBody, resultImageCid, resultImagePath);
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

    private MimeMessage buildMessage(
            String to, String subject, String htmlBody, String textBody, String resultImageCid, String resultImagePath
    ) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);
            helper.addInline(LOGO_CID, new ClassPathResource(LOGO_PATH));
            helper.addInline(resultImageCid, new ClassPathResource(resultImagePath));
            helper.addInline(FOOTER_CID, new ClassPathResource(FOOTER_PATH));
            return message;
        } catch (MessagingException exception) {
            return null;
        }
    }

    private String buildHtmlBody(
            String resultImageCid, String restaurantName, String mealDate, String mealTime, String restaurantAddress
    ) {
        return """
                <div style="max-width:480px;margin:0 auto;font-family:-apple-system,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;">
                  <div style="padding:24px 24px 8px;text-align:center;">
                    <img src="cid:%s" alt="밥풀" style="height:40px;">
                  </div>
                  <div style="padding:0 24px;text-align:center;">
                    <img src="cid:%s" alt="" style="width:100%%;max-width:432px;">
                  </div>
                  <div style="margin:8px 24px 24px;padding:20px 24px;border:1px solid #eee;border-radius:16px;">
                    <table style="width:100%%;border-collapse:collapse;">
                      <tr>
                        <td style="padding:8px 0;color:#888;font-size:14px;">식당명</td>
                        <td style="padding:8px 0;text-align:right;font-weight:600;font-size:14px;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px 0;color:#888;font-size:14px;">예약 날짜</td>
                        <td style="padding:8px 0;text-align:right;font-weight:600;font-size:14px;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px 0;color:#888;font-size:14px;">식사 시작 시간</td>
                        <td style="padding:8px 0;text-align:right;font-weight:600;font-size:14px;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px 0;color:#888;font-size:14px;">주소</td>
                        <td style="padding:8px 0;text-align:right;font-weight:600;font-size:14px;">%s</td>
                      </tr>
                    </table>
                  </div>
                  <img src="cid:%s" alt="밥풀 - 혼밥이 모여, 한 테이블이 되는 곳" style="width:100%%;display:block;">
                </div>
                """.formatted(LOGO_CID, resultImageCid, restaurantName, mealDate, mealTime, restaurantAddress, FOOTER_CID);
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
