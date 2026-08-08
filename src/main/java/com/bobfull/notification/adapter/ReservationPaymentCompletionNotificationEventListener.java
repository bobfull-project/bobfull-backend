package com.bobfull.notification.adapter;

import com.bobfull.notification.config.NotificationAsyncConfig;
import com.bobfull.reservation.event.ReservationPaymentCompletedEvent;
import com.bobfull.reservation.service.ReservationNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 완료(CREATE 접수·JOIN 참여) 이메일 안내를 트랜잭션 커밋 이후에만 수행한다(Issue #168 V2).
 * {@code ReservationConfirmationService#confirm}의 핵심 트랜잭션이 커밋되지 않으면(롤백되면)
 * 이 리스너 자체가 실행되지 않는다. {@code @Async}로 별도 스레드 풀에서 실행해, SMTP 통신 지연이
 * 결제 완료 요청 스레드를 막지 않는다. 이메일 발송이 실패해도 예외를 다시 던지지 않고 로그만
 * 남긴다.
 */
@Component
public class ReservationPaymentCompletionNotificationEventListener {
    private static final Logger log = LoggerFactory.getLogger(ReservationPaymentCompletionNotificationEventListener.class);

    private final ReservationNotificationService notificationService;

    public ReservationPaymentCompletionNotificationEventListener(ReservationNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async(NotificationAsyncConfig.EMAIL_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ReservationPaymentCompletedEvent event) {
        try {
            switch (event.purpose()) {
                case CREATE -> notificationService.notifyReservationCreated(
                        event.reservationId(), event.reservationParticipantId());
                case JOIN -> notificationService.notifyParticipationCompleted(
                        event.reservationId(), event.reservationParticipantId());
            }
        } catch (RuntimeException exception) {
            log.error("event=RESERVATION_NOTIFICATION_EVENT_FAILED reservationId={} purpose={}",
                    event.reservationId(), event.purpose());
        }
    }
}
