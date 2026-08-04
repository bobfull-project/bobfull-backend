package com.bobfull.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.reservation.dto.CancellationScope;
import com.bobfull.reservation.dto.ReservationCancellationRequest;
import com.bobfull.reservation.dto.ReservationCancellationResponse;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationParticipant;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationCancellationServiceTest {

    @Mock
    private ReservationCancellationTransactionService transactionService;

    @Mock
    private ReservationCancellationRefundPort reservationCancellationRefundPort;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationParticipantRepository reservationParticipantRepository;

    private ReservationCancellationService service() {
        return new ReservationCancellationService(
                transactionService, reservationCancellationRefundPort, reservationRepository, reservationParticipantRepository);
    }

    @Test
    void 취소를_접수하고_환불을_요청해_CANCEL_REQUESTED_응답을_반환한다() {
        // given
        RefundRequestCommand command = new RefundRequestCommand(10L, List.of(20L), 1L, "사유");
        ReservationCancellationTransactionService.CancellationAcceptance acceptance =
                new ReservationCancellationTransactionService.CancellationAcceptance(
                        10L, 20L, CancellationScope.PARTICIPATION, command);
        given(transactionService.accept(1L, 10L, new ReservationCancellationRequest("사유"))).willReturn(acceptance);
        given(reservationCancellationRefundPort.requestRefunds(command))
                .willReturn(List.of(new RefundRequestResult(20L, "REQUESTED")));

        // when
        ReservationCancellationResponse response =
                service().cancel(1L, 10L, new ReservationCancellationRequest("사유"));

        // then
        assertThat(response.reservationId()).isEqualTo(10L);
        assertThat(response.participationId()).isEqualTo(20L);
        assertThat(response.participationStatus()).isEqualTo(ParticipationStatus.CANCEL_REQUESTED);
        assertThat(response.cancellationScope()).isEqualTo(CancellationScope.PARTICIPATION);
        assertThat(response.refundStatus()).isEqualTo("REQUESTED");
    }

    @Test
    void 접수가_먼저_커밋된_뒤_환불_포트를_호출한다() {
        // given
        RefundRequestCommand command = new RefundRequestCommand(10L, List.of(20L, 21L), 1L, "사유");
        ReservationCancellationTransactionService.CancellationAcceptance acceptance =
                new ReservationCancellationTransactionService.CancellationAcceptance(
                        10L, 20L, CancellationScope.RESERVATION, command);
        given(transactionService.accept(1L, 10L, new ReservationCancellationRequest("사유"))).willReturn(acceptance);
        given(reservationCancellationRefundPort.requestRefunds(command)).willReturn(List.of(
                new RefundRequestResult(20L, "REQUESTED"), new RefundRequestResult(21L, "REQUESTED")));

        // when
        service().cancel(1L, 10L, new ReservationCancellationRequest("사유"));

        // then: accept()가 requestRefunds()보다 먼저 호출됐는지 순서를 확인한다
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(transactionService, reservationCancellationRefundPort);
        inOrder.verify(transactionService).accept(1L, 10L, new ReservationCancellationRequest("사유"));
        inOrder.verify(reservationCancellationRefundPort).requestRefunds(command);
    }

    @Test
    void 환불_포트가_예외를_던지면_그대로_전파한다() {
        // given: 접수 트랜잭션은 이미 커밋되어 있으므로 여기서 예외가 나도 되돌릴 상태가 없다
        RefundRequestCommand command = new RefundRequestCommand(10L, List.of(20L), 1L, "사유");
        ReservationCancellationTransactionService.CancellationAcceptance acceptance =
                new ReservationCancellationTransactionService.CancellationAcceptance(
                        10L, 20L, CancellationScope.PARTICIPATION, command);
        given(transactionService.accept(1L, 10L, new ReservationCancellationRequest("사유"))).willReturn(acceptance);
        given(reservationCancellationRefundPort.requestRefunds(command))
                .willThrow(new IllegalStateException("환불 요청 실패"));

        // when
        Throwable result = catchThrowable(
                () -> service().cancel(1L, 10L, new ReservationCancellationRequest("사유")));

        // then
        assertThat(result).isInstanceOf(IllegalStateException.class);
        verify(transactionService).accept(1L, 10L, new ReservationCancellationRequest("사유"));
    }

    @Test
    void 참여자를_찾을_수_없으면_완료_처리는_PARTICIPATION_ID_NOT_FOUND를_반환한다() {
        // given
        given(reservationParticipantRepository.findById(30L)).willReturn(Optional.empty());

        // when
        Throwable result = catchThrowable(
                () -> service().completeParticipantCancellation(30L, Instant.parse("2026-08-08T00:00:00Z")));

        // then
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.PARTICIPATION_ID_NOT_FOUND);
    }

    @Test
    void 예약_전체_취소_중_마지막_참여자의_환불이_완료되면_예약도_CANCELLED로_확정한다() {
        // given
        Reservation reservation = reservation(10L);
        reservation.startCancelling();
        ReservationParticipant participant = participant(30L, 10L, 2L);
        participant.requestCancel("개인 사정");
        given(reservationParticipantRepository.findById(30L)).willReturn(Optional.of(participant));
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.existsByReservationIdAndParticipationStatus(10L, ParticipationStatus.CANCEL_REQUESTED))
                .willReturn(false);

        // when
        service().completeParticipantCancellation(30L, Instant.parse("2026-08-08T00:00:00Z"));

        // then
        assertThat(participant.getParticipationStatus()).isEqualTo(ParticipationStatus.CANCELLED);
        assertThat(reservation.isCancelled()).isTrue();
    }

    @Test
    void 다른_참여자가_아직_환불_대기_중이면_예약은_CANCELLING을_유지한다() {
        // given
        Reservation reservation = reservation(10L);
        reservation.startCancelling();
        ReservationParticipant participant = participant(30L, 10L, 2L);
        participant.requestCancel("개인 사정");
        given(reservationParticipantRepository.findById(30L)).willReturn(Optional.of(participant));
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.existsByReservationIdAndParticipationStatus(10L, ParticipationStatus.CANCEL_REQUESTED))
                .willReturn(true);

        // when
        service().completeParticipantCancellation(30L, Instant.parse("2026-08-08T00:00:00Z"));

        // then
        assertThat(participant.getParticipationStatus()).isEqualTo(ParticipationStatus.CANCELLED);
        assertThat(reservation.isCancelling()).isTrue();
    }

    @Test
    void 예약이_CANCELLING이_아닌_단일_참여자_취소_완료는_예약_상태를_바꾸지_않는다() {
        // given: 모집 OPEN 상태에서 추가 참여자 1명만 취소를 접수한 경우
        Reservation reservation = reservation(10L);
        ReservationParticipant participant = participant(30L, 10L, 2L);
        participant.requestCancel("개인 사정");
        given(reservationParticipantRepository.findById(30L)).willReturn(Optional.of(participant));
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));

        // when
        service().completeParticipantCancellation(30L, Instant.parse("2026-08-08T00:00:00Z"));

        // then
        assertThat(participant.getParticipationStatus()).isEqualTo(ParticipationStatus.CANCELLED);
        assertThat(reservation.isActive()).isTrue();
        verify(reservationParticipantRepository, org.mockito.Mockito.never())
                .existsByReservationIdAndParticipationStatus(any(), any());
    }

    @Test
    void 이미_완료된_참여자에_대한_중복_완료_요청은_아무_일도_하지_않는다() {
        // given: 웹훅 중복 전달 시나리오
        Reservation reservation = reservation(10L);
        reservation.startCancelling();
        reservation.cancel();
        ReservationParticipant participant = participant(30L, 10L, 2L);
        participant.requestCancel("개인 사정");
        participant.completeCancel(Instant.parse("2026-08-08T00:00:00Z"));
        given(reservationParticipantRepository.findById(30L)).willReturn(Optional.of(participant));
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));

        // when
        Throwable result = catchThrowable(() -> service()
                .completeParticipantCancellation(30L, Instant.parse("2026-08-08T00:10:00Z")));

        // then
        assertThat(result).isNull();
        assertThat(participant.getParticipationStatus()).isEqualTo(ParticipationStatus.CANCELLED);
    }

    private Reservation reservation(Long id) {
        Reservation reservation = Reservation.create(200L, 1L);
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

    private ReservationParticipant participant(Long id, Long reservationId, Long memberId) {
        ReservationParticipant participant = ReservationParticipant.create(reservationId, memberId, 1);
        ReflectionTestUtils.setField(participant, "id", id);
        return participant;
    }
}
