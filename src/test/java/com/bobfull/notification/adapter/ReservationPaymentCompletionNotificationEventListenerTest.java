package com.bobfull.notification.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.reservation.event.ReservationPaymentCompletedEvent;
import com.bobfull.reservation.service.ReservationNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationPaymentCompletionNotificationEventListenerTest {

    @Mock
    private ReservationNotificationService notificationService;

    private ReservationPaymentCompletionNotificationEventListener listener() {
        return new ReservationPaymentCompletionNotificationEventListener(notificationService);
    }

    @Test
    void CREATE_이벤트를_받으면_예약_접수_알림을_호출한다() {
        listener().handle(new ReservationPaymentCompletedEvent(10L, 20L, PaymentPurpose.CREATE));

        verify(notificationService).notifyReservationCreated(10L, 20L);
        verify(notificationService, never()).notifyParticipationCompleted(10L, 20L);
    }

    @Test
    void JOIN_이벤트를_받으면_참여_완료_알림을_호출한다() {
        listener().handle(new ReservationPaymentCompletedEvent(10L, 21L, PaymentPurpose.JOIN));

        verify(notificationService).notifyParticipationCompleted(10L, 21L);
        verify(notificationService, never()).notifyReservationCreated(10L, 21L);
    }

    @Test
    void 알림_호출이_실패해도_예외를_밖으로_전파하지_않는다() {
        doThrow(new IllegalStateException("강제 실패(테스트)")).when(notificationService).notifyReservationCreated(10L, 20L);

        assertThatCode(() -> listener().handle(new ReservationPaymentCompletedEvent(10L, 20L, PaymentPurpose.CREATE)))
                .doesNotThrowAnyException();
    }
}
