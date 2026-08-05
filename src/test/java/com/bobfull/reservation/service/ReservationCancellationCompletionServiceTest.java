package com.bobfull.reservation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationCancellationCompletionServiceTest {

    @Mock ReservationRepository reservationRepository;
    @Mock ReservationParticipantRepository participantRepository;
    @Mock ReservationCancellationTransactionService transactionService;
    @Mock Reservation reservation;

    private ReservationCancellationCompletionService service() {
        return new ReservationCancellationCompletionService(
                reservationRepository, participantRepository, transactionService);
    }

    @Test
    void 완료는_Reservation_락_후_조건부_UPDATE로_한번만_처리한다() {
        Instant now = Instant.now();
        when(reservationRepository.findWithLockById(1L)).thenReturn(Optional.of(reservation));
        when(participantRepository.completeCancelIfRequested(2L, now)).thenReturn(0);

        service().complete(1L, 2L, now);

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
        when(participantRepository.existsByReservationIdAndParticipationStatus(
                1L, ParticipationStatus.CANCEL_REQUESTED)).thenReturn(false);

        service().complete(1L, 2L, now);

        verify(reservation).cancel();
        verifyNoInteractions(transactionService);
    }

    @Test
    void 다른_취소요청이_남아있으면_CANCELLING을_유지한다() {
        Instant now = Instant.now();
        when(reservationRepository.findWithLockById(1L)).thenReturn(Optional.of(reservation));
        when(participantRepository.completeCancelIfRequested(2L, now)).thenReturn(1);
        when(reservation.isCancelling()).thenReturn(true);
        when(participantRepository.existsByReservationIdAndParticipationStatus(
                1L, ParticipationStatus.CANCEL_REQUESTED)).thenReturn(true);

        service().complete(1L, 2L, now);

        verify(reservation, never()).cancel();
        verifyNoInteractions(transactionService);
    }

    @Test
    void 개별참여_취소완료면_남은인원으로_Reservation을_재계산한다() {
        Instant now = Instant.now();
        when(reservationRepository.findWithLockById(1L)).thenReturn(Optional.of(reservation));
        when(participantRepository.completeCancelIfRequested(2L, now)).thenReturn(1);
        when(reservation.isCancelling()).thenReturn(false);

        service().complete(1L, 2L, now);

        verify(transactionService).recalculateAfterCompletion(reservation);
    }

    @Test
    void 존재하지_않는_Reservation이면_예외를_던진다() {
        Instant now = Instant.now();
        when(reservationRepository.findWithLockById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().complete(1L, 2L, now))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ReservationErrorCode.RESERVATION_ID_NOT_FOUND);

        verifyNoInteractions(participantRepository, transactionService);
    }
}
