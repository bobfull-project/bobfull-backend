package com.bobfull.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bobfull.reservation.port.ReservationCancellationRefundPort;
import com.bobfull.reservation.port.ReservationCancellationRefundPort.RefundRequestCommand;
import com.bobfull.reservation.service.ReservationCancellationTransactionService.RecruitmentDeadlineAcceptance;
import com.bobfull.reservation.service.ReservationCancellationTransactionService.RecruitmentDeadlineOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecruitmentDeadlineCancellationServiceTest {
    @Mock ReservationCancellationTransactionService transactionService;
    @Mock ReservationCancellationRefundPort refundPort;
    @Mock ReservationNotificationService notificationService;

    private RecruitmentDeadlineCancellationService service() {
        return new RecruitmentDeadlineCancellationService(transactionService, refundPort, notificationService);
    }

    @Test
    void CANCELLED_결과면_환불Port와_취소_알림을_호출한다() {
        RefundRequestCommand command = new RefundRequestCommand(1L, List.of(2L, 3L), 9L, "모집 마감 기준 인원 미달로 자동 취소되었습니다");
        when(transactionService.acceptRecruitmentDeadline(1L)).thenReturn(
                new RecruitmentDeadlineAcceptance(1L, RecruitmentDeadlineOutcome.CANCELLED, command));

        RecruitmentDeadlineOutcome outcome = service().process(1L);

        assertThat(outcome).isEqualTo(RecruitmentDeadlineOutcome.CANCELLED);
        verify(refundPort).requestRefunds(command);
        verify(notificationService).notifyCancelledDueToInsufficientParticipants(1L, List.of(2L, 3L));
        verify(notificationService, never()).notifyConfirmed(any());
    }

    @Test
    void CLOSED_ONLY_결과면_확정_알림만_호출하고_환불Port는_호출하지_않는다() {
        when(transactionService.acceptRecruitmentDeadline(1L)).thenReturn(
                new RecruitmentDeadlineAcceptance(1L, RecruitmentDeadlineOutcome.CLOSED_ONLY, null));

        RecruitmentDeadlineOutcome outcome = service().process(1L);

        assertThat(outcome).isEqualTo(RecruitmentDeadlineOutcome.CLOSED_ONLY);
        verify(refundPort, never()).requestRefunds(any());
        verify(notificationService).notifyConfirmed(1L);
        verify(notificationService, never()).notifyCancelledDueToInsufficientParticipants(any(), any());
    }

    @Test
    void ALREADY_PROCESSED_결과면_환불Port와_알림_모두_호출하지_않는다() {
        when(transactionService.acceptRecruitmentDeadline(1L)).thenReturn(
                new RecruitmentDeadlineAcceptance(1L, RecruitmentDeadlineOutcome.ALREADY_PROCESSED, null));

        RecruitmentDeadlineOutcome outcome = service().process(1L);

        assertThat(outcome).isEqualTo(RecruitmentDeadlineOutcome.ALREADY_PROCESSED);
        verify(refundPort, never()).requestRefunds(any());
        verify(notificationService, never()).notifyConfirmed(any());
        verify(notificationService, never()).notifyCancelledDueToInsufficientParticipants(any(), any());
    }
}
