package com.bobfull.notification.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.bobfull.reservation.event.RecruitmentDeadlineCancelledEvent;
import com.bobfull.reservation.event.RecruitmentDeadlineConfirmedEvent;
import com.bobfull.reservation.service.ReservationNotificationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecruitmentDeadlineNotificationEventListenerTest {

    @Mock
    private ReservationNotificationService notificationService;

    private RecruitmentDeadlineNotificationEventListener listener() {
        return new RecruitmentDeadlineNotificationEventListener(notificationService);
    }

    @Test
    void 확정_이벤트를_받으면_확정_알림을_호출한다() {
        listener().handleConfirmed(new RecruitmentDeadlineConfirmedEvent(10L));

        verify(notificationService).notifyConfirmed(10L);
    }

    @Test
    void 확정_알림_호출이_실패해도_예외를_밖으로_전파하지_않는다() {
        doThrow(new IllegalStateException("강제 실패(테스트)")).when(notificationService).notifyConfirmed(10L);

        assertThatCode(() -> listener().handleConfirmed(new RecruitmentDeadlineConfirmedEvent(10L)))
                .doesNotThrowAnyException();
    }

    @Test
    void 취소_이벤트를_받으면_취소_알림을_참여자_ID와_함께_호출한다() {
        listener().handleCancelled(new RecruitmentDeadlineCancelledEvent(10L, List.of(1L, 2L)));

        verify(notificationService).notifyCancelledDueToInsufficientParticipants(10L, List.of(1L, 2L));
    }

    @Test
    void 취소_알림_호출이_실패해도_예외를_밖으로_전파하지_않는다() {
        doThrow(new IllegalStateException("강제 실패(테스트)"))
                .when(notificationService).notifyCancelledDueToInsufficientParticipants(10L, List.of(1L));

        assertThatCode(() -> listener().handleCancelled(new RecruitmentDeadlineCancelledEvent(10L, List.of(1L))))
                .doesNotThrowAnyException();
    }
}
