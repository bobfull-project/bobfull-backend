package com.bobfull.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.reservation.dto.CancellationScope;
import com.bobfull.reservation.dto.ReservationCancellationRequest;
import com.bobfull.reservation.dto.ReservationCancellationResponse;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.RecruitmentStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationParticipant;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.port.ReservationCancellationRefundPort;
import com.bobfull.reservation.port.ReservationCancellationRefundPort.RefundRequestResult;
import com.bobfull.reservation.port.ReservationCapacityReader;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationCancellationServiceTest {

    private static final Long TIME_SLOT_ID = 200L;
    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant CANCELLABLE_START_AT = Instant.parse("2026-08-08T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationParticipantRepository reservationParticipantRepository;

    @Mock
    private ReservationCapacityReader reservationCapacityReader;

    @Mock
    private ReservationCancellationRefundPort reservationCancellationRefundPort;

    private ReservationCancellationService service() {
        return new ReservationCancellationService(
                reservationRepository, reservationParticipantRepository,
                reservationCapacityReader, reservationCancellationRefundPort, CLOCK);
    }

    @Test
    void 예약을_찾을_수_없으면_RESERVATION_ID_NOT_FOUND를_반환한다() {
        // given
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.empty());

        // when
        Throwable result = catchThrowable(
                () -> service().cancel(1L, 10L, new ReservationCancellationRequest("사유")));

        // then
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_ID_NOT_FOUND);
    }

    @Test
    void 이미_취소된_예약이면_RESERVATION_ALREADY_CANCELLED를_반환한다() {
        // given
        Reservation reservation = reservation(10L, 1L);
        reservation.cancel();
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));

        // when
        Throwable result = catchThrowable(
                () -> service().cancel(1L, 10L, new ReservationCancellationRequest("사유")));

        // then
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.RESERVATION_ALREADY_CANCELLED);
    }

    @Test
    void 식사_시작_2시간_이내면_CANCELLATION_DEADLINE_PASSED를_반환한다() {
        // given
        Reservation reservation = reservation(10L, 1L);
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationCapacityReader.readTimeSlotStartAt(TIME_SLOT_ID))
                .willReturn(Instant.parse("2026-08-08T01:30:00Z"));

        // when
        Throwable result = catchThrowable(
                () -> service().cancel(1L, 10L, new ReservationCancellationRequest("사유")));

        // then
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.CANCELLATION_DEADLINE_PASSED);
    }

    @Test
    void 취소_기한_경계_시각에는_취소할_수_있다() {
        // given: startAt - 2h == now
        Reservation reservation = reservation(10L, 1L);
        ReservationParticipant participant = participant(20L, 10L, 1L);
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationCapacityReader.readTimeSlotStartAt(TIME_SLOT_ID))
                .willReturn(Instant.parse("2026-08-08T02:00:00Z"));
        given(reservationParticipantRepository.findByReservationIdAndMemberId(10L, 1L))
                .willReturn(Optional.of(participant));
        given(reservationParticipantRepository.findAllByReservationIdAndParticipationStatus(10L, ParticipationStatus.RESERVED))
                .willReturn(List.of(participant));
        given(reservationCancellationRefundPort.requestRefunds(any()))
                .willReturn(List.of(new RefundRequestResult(20L, "REQUESTED")));

        // when
        ReservationCancellationResponse response = service().cancel(1L, 10L, new ReservationCancellationRequest("사유"));

        // then
        assertThat(response.cancellationScope()).isEqualTo(CancellationScope.RESERVATION);
    }

    @Test
    void 본인_참여를_찾을_수_없으면_PARTICIPATION_NOT_FOUND를_반환한다() {
        // given
        Reservation reservation = reservation(10L, 1L);
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationCapacityReader.readTimeSlotStartAt(TIME_SLOT_ID)).willReturn(CANCELLABLE_START_AT);
        given(reservationParticipantRepository.findByReservationIdAndMemberId(10L, 2L)).willReturn(Optional.empty());

        // when
        Throwable result = catchThrowable(
                () -> service().cancel(2L, 10L, new ReservationCancellationRequest("사유")));

        // then
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.PARTICIPATION_NOT_FOUND);
    }

    @Test
    void 이미_취소된_참여자는_PARTICIPATION_ALREADY_CANCELLED를_반환한다() {
        // given
        Reservation reservation = reservation(10L, 1L);
        ReservationParticipant participant = participant(21L, 10L, 2L);
        participant.cancel("이전 취소", NOW.minusSeconds(60));
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationCapacityReader.readTimeSlotStartAt(TIME_SLOT_ID)).willReturn(CANCELLABLE_START_AT);
        given(reservationParticipantRepository.findByReservationIdAndMemberId(10L, 2L)).willReturn(Optional.of(participant));

        // when
        Throwable result = catchThrowable(
                () -> service().cancel(2L, 10L, new ReservationCancellationRequest("사유")));

        // then
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.PARTICIPATION_ALREADY_CANCELLED);
    }

    @Test
    void NO_SHOW_참여자는_CANCELLATION_NOT_ALLOWED를_반환한다() {
        // given
        Reservation reservation = reservation(10L, 1L);
        ReservationParticipant participant = participant(21L, 10L, 2L);
        ReflectionTestUtils.setField(participant, "participationStatus", ParticipationStatus.NO_SHOW);
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationCapacityReader.readTimeSlotStartAt(TIME_SLOT_ID)).willReturn(CANCELLABLE_START_AT);
        given(reservationParticipantRepository.findByReservationIdAndMemberId(10L, 2L)).willReturn(Optional.of(participant));

        // when
        Throwable result = catchThrowable(
                () -> service().cancel(2L, 10L, new ReservationCancellationRequest("사유")));

        // then
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(ReservationErrorCode.CANCELLATION_NOT_ALLOWED);
    }

    @Test
    void 최초_예약자가_취소하면_예약_전체와_유효_참여자_전원이_취소되고_모두_환불을_요청한다() {
        // given
        Reservation reservation = reservation(10L, 1L);
        ReservationParticipant creator = participant(20L, 10L, 1L);
        ReservationParticipant other = participant(21L, 10L, 2L);
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationCapacityReader.readTimeSlotStartAt(TIME_SLOT_ID)).willReturn(CANCELLABLE_START_AT);
        given(reservationParticipantRepository.findByReservationIdAndMemberId(10L, 1L)).willReturn(Optional.of(creator));
        given(reservationParticipantRepository.findAllByReservationIdAndParticipationStatus(10L, ParticipationStatus.RESERVED))
                .willReturn(List.of(creator, other));
        given(reservationCancellationRefundPort.requestRefunds(any())).willReturn(List.of(
                new RefundRequestResult(20L, "REQUESTED"), new RefundRequestResult(21L, "REQUESTED")));

        // when
        ReservationCancellationResponse response = service().cancel(1L, 10L, new ReservationCancellationRequest("개인 사정"));

        // then
        assertThat(response.cancellationScope()).isEqualTo(CancellationScope.RESERVATION);
        assertThat(response.refundStatus()).isEqualTo("REQUESTED");
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(creator.getParticipationStatus()).isEqualTo(ParticipationStatus.CANCELLED);
        assertThat(other.getParticipationStatus()).isEqualTo(ParticipationStatus.CANCELLED);
        assertThat(creator.getCancelReason()).isEqualTo("개인 사정");
        assertThat(other.getCancelReason()).isEqualTo("개인 사정");

        ReservationCancellationRefundPort.RefundRequestCommand command =
                captureRefundCommand();
        assertThat(command.reservationParticipantIds()).containsExactlyInAnyOrder(20L, 21L);
        assertThat(command.requesterMemberId()).isEqualTo(1L);
    }

    @Test
    void 추가_참여자_취소_후_확정_기준_미달이면_RECRUITING으로_되돌아간다() {
        // given: capacity 4, threshold 3. CONFIRMED 상태에서 한 명 취소해 잔여 2명으로 기준 미달.
        Reservation reservation = reservation(10L, 1L);
        reservation.confirm();
        ReservationParticipant participant = participant(22L, 10L, 3L);
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationCapacityReader.readTimeSlotStartAt(TIME_SLOT_ID)).willReturn(CANCELLABLE_START_AT);
        given(reservationParticipantRepository.findByReservationIdAndMemberId(10L, 3L)).willReturn(Optional.of(participant));
        given(reservationCancellationRefundPort.requestRefunds(any()))
                .willReturn(List.of(new RefundRequestResult(22L, "REQUESTED")));
        given(reservationCapacityReader.readTableCapacity(TIME_SLOT_ID)).willReturn(4);
        given(reservationParticipantRepository.sumPartySize(10L, ParticipationStatus.RESERVED)).willReturn(2);

        // when
        ReservationCancellationResponse response = service().cancel(3L, 10L, new ReservationCancellationRequest("사유"));

        // then
        assertThat(response.cancellationScope()).isEqualTo(CancellationScope.PARTICIPATION);
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.RECRUITING);
        assertThat(reservation.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.OPEN);
    }

    @Test
    void 추가_참여자_취소_후_확정_기준_이상이면_CONFIRMED를_유지한다() {
        // given: capacity 4, threshold 3. 잔여 3명으로 기준 충족.
        Reservation reservation = reservation(10L, 1L);
        reservation.confirm();
        ReservationParticipant participant = participant(22L, 10L, 3L);
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationCapacityReader.readTimeSlotStartAt(TIME_SLOT_ID)).willReturn(CANCELLABLE_START_AT);
        given(reservationParticipantRepository.findByReservationIdAndMemberId(10L, 3L)).willReturn(Optional.of(participant));
        given(reservationCancellationRefundPort.requestRefunds(any()))
                .willReturn(List.of(new RefundRequestResult(22L, "REQUESTED")));
        given(reservationCapacityReader.readTableCapacity(TIME_SLOT_ID)).willReturn(4);
        given(reservationParticipantRepository.sumPartySize(10L, ParticipationStatus.RESERVED)).willReturn(3);

        // when
        service().cancel(3L, 10L, new ReservationCancellationRequest("사유"));

        // then
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void 모집이_CLOSED이고_기준_미달이면_재오픈하지_않고_남은_참여자를_전액_환불하며_예약_전체를_취소한다() {
        // given: CONFIRMED + CLOSED, capacity 4, threshold 3. 잔여 2명으로 기준 미달.
        Reservation reservation = reservation(10L, 1L);
        reservation.confirm();
        reservation.closeRecruitment();
        ReservationParticipant cancelling = participant(23L, 10L, 4L);
        ReservationParticipant remaining = participant(24L, 10L, 1L);
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationCapacityReader.readTimeSlotStartAt(TIME_SLOT_ID)).willReturn(CANCELLABLE_START_AT);
        given(reservationParticipantRepository.findByReservationIdAndMemberId(10L, 4L)).willReturn(Optional.of(cancelling));
        given(reservationCancellationRefundPort.requestRefunds(any()))
                .willReturn(List.of(new RefundRequestResult(23L, "REQUESTED")))
                .willReturn(List.of(new RefundRequestResult(24L, "REQUESTED")));
        given(reservationCapacityReader.readTableCapacity(TIME_SLOT_ID)).willReturn(4);
        given(reservationParticipantRepository.sumPartySize(10L, ParticipationStatus.RESERVED)).willReturn(2);
        given(reservationParticipantRepository.findAllByReservationIdAndParticipationStatus(10L, ParticipationStatus.RESERVED))
                .willReturn(List.of(remaining));

        // when
        ReservationCancellationResponse response = service().cancel(4L, 10L, new ReservationCancellationRequest("사유"));

        // then
        assertThat(response.cancellationScope()).isEqualTo(CancellationScope.PARTICIPATION);
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservation.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.CLOSED);
        assertThat(remaining.getParticipationStatus()).isEqualTo(ParticipationStatus.CANCELLED);
    }

    @Test
    void 모집이_CLOSED이고_기준_이상이면_CONFIRMED_CLOSED를_유지한다() {
        // given
        Reservation reservation = reservation(10L, 1L);
        reservation.confirm();
        reservation.closeRecruitment();
        ReservationParticipant participant = participant(25L, 10L, 4L);
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationCapacityReader.readTimeSlotStartAt(TIME_SLOT_ID)).willReturn(CANCELLABLE_START_AT);
        given(reservationParticipantRepository.findByReservationIdAndMemberId(10L, 4L)).willReturn(Optional.of(participant));
        given(reservationCancellationRefundPort.requestRefunds(any()))
                .willReturn(List.of(new RefundRequestResult(25L, "REQUESTED")));
        given(reservationCapacityReader.readTableCapacity(TIME_SLOT_ID)).willReturn(4);
        given(reservationParticipantRepository.sumPartySize(10L, ParticipationStatus.RESERVED)).willReturn(3);

        // when
        service().cancel(4L, 10L, new ReservationCancellationRequest("사유"));

        // then
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.CLOSED);
        verify(reservationParticipantRepository, never())
                .findAllByReservationIdAndParticipationStatus(10L, ParticipationStatus.RESERVED);
    }

    @Test
    void 환불_요청이_실패하면_참여_상태가_변경되지_않는다() {
        // given
        Reservation reservation = reservation(10L, 1L);
        ReservationParticipant participant = participant(26L, 10L, 5L);
        given(reservationRepository.findWithLockById(10L)).willReturn(Optional.of(reservation));
        given(reservationCapacityReader.readTimeSlotStartAt(TIME_SLOT_ID)).willReturn(CANCELLABLE_START_AT);
        given(reservationParticipantRepository.findByReservationIdAndMemberId(10L, 5L)).willReturn(Optional.of(participant));
        given(reservationCancellationRefundPort.requestRefunds(any()))
                .willThrow(new IllegalStateException("환불 요청 실패"));

        // when
        Throwable result = catchThrowable(
                () -> service().cancel(5L, 10L, new ReservationCancellationRequest("사유")));

        // then
        assertThat(result).isInstanceOf(IllegalStateException.class);
        assertThat(participant.getParticipationStatus()).isEqualTo(ParticipationStatus.RESERVED);
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.RECRUITING);
    }

    private ReservationCancellationRefundPort.RefundRequestCommand captureRefundCommand() {
        org.mockito.ArgumentCaptor<ReservationCancellationRefundPort.RefundRequestCommand> captor =
                org.mockito.ArgumentCaptor.forClass(ReservationCancellationRefundPort.RefundRequestCommand.class);
        verify(reservationCancellationRefundPort).requestRefunds(captor.capture());
        return captor.getValue();
    }

    private Reservation reservation(Long id, Long creatorMemberId) {
        Reservation reservation = Reservation.create(TIME_SLOT_ID, creatorMemberId);
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

    private ReservationParticipant participant(Long id, Long reservationId, Long memberId) {
        ReservationParticipant participant = ReservationParticipant.create(reservationId, memberId, 1);
        ReflectionTestUtils.setField(participant, "id", id);
        return participant;
    }
}
