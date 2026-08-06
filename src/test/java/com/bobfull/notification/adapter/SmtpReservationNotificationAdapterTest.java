package com.bobfull.notification.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bobfull.reservation.port.ReservationNotificationPort.Recipient;
import com.bobfull.reservation.port.ReservationNotificationPort.ReservationResultNotification;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpReservationNotificationAdapterTest {
    private static final Instant MEAL_START_AT = Instant.parse("2026-08-10T10:00:00Z");

    @Mock JavaMailSender mailSender;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(SmtpReservationNotificationAdapter.class)).addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        ((Logger) LoggerFactory.getLogger(SmtpReservationNotificationAdapter.class)).detachAppender(logAppender);
    }

    private SmtpReservationNotificationAdapter adapter() {
        return new SmtpReservationNotificationAdapter(mailSender, "no-reply@bobfull.com");
    }

    private void givenMimeMessage() {
        given(mailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));
    }

    @Test
    void 정상_발송이면_참여자별로_메일을_한_번씩_보낸다() {
        givenMimeMessage();
        ReservationResultNotification notification = notification(
                new Recipient(1L, "a@bobfull.com", "회원A"), new Recipient(2L, "b@bobfull.com", "회원B"));

        adapter().notifyConfirmed(notification);

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void 발송이_계속_실패하면_참여자당_최대_3회까지만_재시도한다() {
        givenMimeMessage();
        ReservationResultNotification notification = notification(new Recipient(1L, "a@bobfull.com", "회원A"));
        doThrow(new MailSendException("boom")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> adapter().notifyConfirmed(notification)).doesNotThrowAnyException();

        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void 한_참여자의_발송_실패가_다른_참여자_발송을_막지_않는다() {
        givenMimeMessage();
        ReservationResultNotification notification = notification(
                new Recipient(1L, "fail@bobfull.com", "회원A"), new Recipient(2L, "ok@bobfull.com", "회원B"));
        doAnswer(invocation -> {
            MimeMessage message = invocation.getArgument(0);
            if ("fail@bobfull.com".equals(message.getRecipients(Message.RecipientType.TO)[0].toString())) {
                throw new MailSendException("boom");
            }
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        adapter().notifyCancelledDueToInsufficientParticipants(notification);

        // 실패한 참여자 3회 재시도 + 성공한 참여자 1회 = 총 4회
        verify(mailSender, times(4)).send(any(MimeMessage.class));
    }

    @Test
    void 발송_실패_로그에_이메일_주소를_남기지_않는다() {
        givenMimeMessage();
        ReservationResultNotification notification = notification(new Recipient(1L, "secret@bobfull.com", "회원A"));
        doThrow(new MailSendException("boom")).when(mailSender).send(any(MimeMessage.class));

        adapter().notifyConfirmed(notification);

        assertThat(logAppender.list).isNotEmpty();
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("secret@bobfull.com") || message.contains("@bobfull.com"));
    }

    private ReservationResultNotification notification(Recipient... recipients) {
        return new ReservationResultNotification(1L, "밥풀식당", MEAL_START_AT, List.of(recipients));
    }
}
