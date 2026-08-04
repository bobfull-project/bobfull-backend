package com.bobfull.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.reservation.dto.CancellationScope;
import com.bobfull.reservation.dto.ReservationCancellationRequest;
import com.bobfull.reservation.dto.ReservationCancellationResponse;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.port.ReservationCancellationRefundPort;
import com.bobfull.reservation.port.ReservationCancellationRefundPort.RefundRequestCommand;
import com.bobfull.reservation.port.ReservationCancellationRefundPort.RefundRequestResult;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationCancellationServiceTest {
    @Mock ReservationCancellationTransactionService transactionService;
    @Mock ReservationCancellationRefundPort refundPort;
    @Mock ReservationRepository reservationRepository;
    @Mock ReservationParticipantRepository participantRepository;
    @Mock Reservation reservation;

    private ReservationCancellationService service() {
        return new ReservationCancellationService(transactionService, refundPort, reservationRepository, participantRepository);
    }

    @Test
    void 취소_접수_커밋_후_환불Port를_호출하고_CANCEL_REQUESTED를_반환한다() {
        RefundRequestCommand command = new RefundRequestCommand(1L, List.of(2L), 3L, "사유");
        when(transactionService.accept(eq(3L), eq(1L), any())).thenReturn(
                new ReservationCancellationTransactionService.CancellationAcceptance(1L, 2L, CancellationScope.PARTICIPATION, command));
        when(refundPort.requestRefunds(command)).thenReturn(List.of(new RefundRequestResult(2L, "PROCESSING")));
        ReservationCancellationResponse result = service().cancel(3L, 1L, new ReservationCancellationRequest("사유"));
        assertThat(result.participationStatus()).isEqualTo(ParticipationStatus.CANCEL_REQUESTED);
        InOrder order = inOrder(transactionService, refundPort);
        order.verify(transactionService).accept(eq(3L), eq(1L), any());
        order.verify(refundPort).requestRefunds(command);
    }

    @Test
    void 환불Port_예외는_전달되고_구형_원상복구를_수행하지_않는다() {
        RefundRequestCommand command = new RefundRequestCommand(1L, List.of(2L), 3L, "사유");
        when(transactionService.accept(eq(3L), eq(1L), any())).thenReturn(
                new ReservationCancellationTransactionService.CancellationAcceptance(1L, 2L, CancellationScope.PARTICIPATION, command));
        when(refundPort.requestRefunds(command)).thenThrow(new CustomException(ReservationErrorCode.CANCELLATION_NOT_ALLOWED));
        assertThatThrownBy(() -> service().cancel(3L, 1L, new ReservationCancellationRequest("사유"))).isInstanceOf(CustomException.class);
    }

    @Test
    void 완료는_Reservation_락_후_조건부_UPDATE로_한번만_처리한다() {
        Instant now = Instant.now();
        when(reservationRepository.findWithLockById(1L)).thenReturn(Optional.of(reservation));
        when(participantRepository.completeCancelIfRequested(2L, now)).thenReturn(0);
        service().completeParticipantCancellation(1L, 2L, now);
        InOrder order = inOrder(reservationRepository, participantRepository);
        order.verify(reservationRepository).findWithLockById(1L);
        order.verify(participantRepository).completeCancelIfRequested(2L, now);
        verifyNoInteractions(transactionService);
    }

    @Test
    void 마지막_요청완료면_CANCELLING_Reservation을_CANCELLED로_확정한다() {
        Instant now = Instant.now();
        when(reservationRepository.findWithLockById(1L)).thenReturn(Optional.of(reservation));
        when(participantRepository.completeCancelIfRequested(2L, now)).thenReturn(1);
        when(reservation.isCancelling()).thenReturn(true);
        when(participantRepository.existsByReservationIdAndParticipationStatus(1L, ParticipationStatus.CANCEL_REQUESTED)).thenReturn(false);
        service().completeParticipantCancellation(1L, 2L, now);
        org.mockito.Mockito.verify(reservation).cancel();
    }
}
