package com.bobfull.notification.adapter;

import com.bobfull.notification.config.NotificationAsyncConfig;
import com.bobfull.reservation.event.RecruitmentDeadlineCancelledEvent;
import com.bobfull.reservation.event.RecruitmentDeadlineConfirmedEvent;
import com.bobfull.reservation.service.ReservationNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 모집 마감 처리 결과(확정·인원 미달 취소) 이메일 안내를 트랜잭션 커밋 이후에만 수행한다
 * (Issue #168 V2). {@code acceptRecruitmentDeadline}의 핵심 트랜잭션이 커밋되지 않으면(롤백되면)
 * 이 리스너 자체가 실행되지 않는다. {@code @Async}로 별도 스레드 풀에서 실행해, SMTP 통신 지연이
 * 스케줄러 실행 스레드를 막지 않는다. 이메일 발송이 실패해도 예외를 다시 던지지 않고 로그만
 * 남긴다 — 이미 커밋된 예약 상태나, 별도로 처리되는 환불 요청에는 영향이 없어야 한다.
 */
@Component
public class RecruitmentDeadlineNotificationEventListener {
    private static final Logger log = LoggerFactory.getLogger(RecruitmentDeadlineNotificationEventListener.class);

    private final ReservationNotificationService notificationService;

    public RecruitmentDeadlineNotificationEventListener(ReservationNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async(NotificationAsyncConfig.EMAIL_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleConfirmed(RecruitmentDeadlineConfirmedEvent event) {
        try {
            notificationService.notifyConfirmed(event.reservationId());
        } catch (RuntimeException exception) {
            log.error("event=RESERVATION_NOTIFICATION_EVENT_FAILED reservationId={} type=CONFIRMED", event.reservationId());
        }
    }

    @Async(NotificationAsyncConfig.EMAIL_TASK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCancelled(RecruitmentDeadlineCancelledEvent event) {
        try {
            notificationService.notifyCancelledDueToInsufficientParticipants(
                    event.reservationId(), event.reservationParticipantIds());
        } catch (RuntimeException exception) {
            log.error("event=RESERVATION_NOTIFICATION_EVENT_FAILED reservationId={} type=CANCELLED", event.reservationId());
        }
    }
}
