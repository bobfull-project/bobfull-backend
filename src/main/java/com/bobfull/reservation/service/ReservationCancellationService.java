package com.bobfull.reservation.service;

import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.reservation.dto.CancellationScope;
import com.bobfull.reservation.dto.ReservationCancellationRequest;
import com.bobfull.reservation.dto.ReservationCancellationResponse;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.RecruitmentStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationParticipant;
import com.bobfull.reservation.policy.ReservationCapacityPolicy;
import com.bobfull.reservation.port.ReservationCancellationRefundPort;
import com.bobfull.reservation.port.ReservationCapacityReader;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증된 MEMBER 본인의 예약 참여 취소를 처리한다(Issue #131). 최초 예약자 취소는 예약 전체를,
 * 추가 참여자 취소는 본인 참여만 CANCELLED로 전환하며, 환불은 결제 도메인 소유
 * {@link ReservationCancellationRefundPort}에만 위임한다.
 */
@Service
public class ReservationCancellationService {

    private static final Duration CANCELLATION_DEADLINE = Duration.ofHours(2);

    private final ReservationRepository reservationRepository;
    private final ReservationParticipantRepository reservationParticipantRepository;
    private final ReservationCapacityReader reservationCapacityReader;
    private final ReservationCancellationRefundPort reservationCancellationRefundPort;
    private final Clock clock;

    public ReservationCancellationService(
            ReservationRepository reservationRepository,
            ReservationParticipantRepository reservationParticipantRepository,
            ReservationCapacityReader reservationCapacityReader,
            ReservationCancellationRefundPort reservationCancellationRefundPort,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationParticipantRepository = reservationParticipantRepository;
        this.reservationCapacityReader = reservationCapacityReader;
        this.reservationCancellationRefundPort = reservationCancellationRefundPort;
        this.clock = clock;
    }

    // 락 순서: Reservation 단독(ADR 0001 "복수 비관적 락의 획득 순서" 참고). 이 흐름은 Payment·Refund를
    // 직접 잠그지 않고 outbound port로만 환불을 요청하므로, 결제 도메인 쪽 락 획득은 그 Adapter
    // 구현(#45)의 책임이다. Reservation을 트랜잭션의 첫 잠금 조회로 만들어 동시 JOIN·확정 흐름과
    // 같은 행을 두고 직렬화한다(JOIN 확정도 Reservation을 잠그는 것과 동일한 전제).
    @Transactional
    public ReservationCancellationResponse cancel(Long memberId, Long reservationId, ReservationCancellationRequest request) {
        Reservation reservation = findReservationWithLockOrThrow(reservationId);
        validateReservationNotCancelled(reservation);

        ReservationParticipant actingParticipant = findParticipantOrThrow(reservationId, memberId);
        validateParticipantCancellable(actingParticipant);

        validateCancellationDeadline(reservation.getTimeSlotId());

        Instant now = Instant.now(clock);
        String reason = request.reason();

        if (reservation.isCreatedBy(memberId)) {
            return cancelEntireReservation(reservation, actingParticipant, reason, now);
        }
        return cancelByParticipant(reservation, actingParticipant, reason, now);
    }

    /**
     * 예약에 속한 유효 참여자 전원을 환불 요청하고 CANCELLED로 전환한 뒤 Reservation도 CANCELLED로
     * 전환한다. 최초 예약자 취소, 그리고 추가 참여자 취소로 모집 CLOSED 상태의 확정 기준 미달이
     * 되는 경우 모두 이 경로를 함께 사용해, 취소 대상 전원을 단 한 번의 환불 요청으로 묶는다
     * (부분 성공으로 인한 정합성 붕괴 방지).
     */
    private ReservationCancellationResponse cancelEntireReservation(
            Reservation reservation, ReservationParticipant actingParticipant, String reason, Instant now
    ) {
        List<ReservationParticipant> validParticipants = reservationParticipantRepository
                .findAllByReservationIdAndParticipationStatus(reservation.getId(), ParticipationStatus.RESERVED);
        Map<Long, String> refundStatusByParticipantId =
                refundAndCancel(reservation.getId(), validParticipants, actingParticipant.getMemberId(), reason, now);
        reservation.cancel();

        return new ReservationCancellationResponse(
                reservation.getId(), actingParticipant.getId(), ParticipationStatus.CANCELLED,
                CancellationScope.RESERVATION, refundStatusByParticipantId.get(actingParticipant.getId()));
    }

    /**
     * 추가 참여자 취소 후 인원·상태를 재계산한다(§0.8, PROJECT_CONTEXT 취소·환불 절). 취소로 인해
     * 모집 CLOSED 상태에서 확정 기준 미달이 될지를 참여자 취소·환불을 실행하기 전에 먼저 판단해,
     * 그 경우 본인을 포함한 전체 유효 참여자를 {@link #cancelEntireReservation}로 한 번에 처리한다
     * (환불 포트를 두 번 호출해 부분 성공이 발생하는 것을 방지). 기준을 유지하거나 모집이 OPEN이면
     * 본인만 환불 요청·CANCELLED 처리하고 RECRUITING/CONFIRMED를 재계산한다.
     */
    private ReservationCancellationResponse cancelByParticipant(
            Reservation reservation, ReservationParticipant actingParticipant, String reason, Instant now
    ) {
        int tableCapacity = reservationCapacityReader.readTableCapacity(reservation.getTimeSlotId());
        int countAfterCancel = reservationParticipantRepository
                .sumPartySize(reservation.getId(), ParticipationStatus.RESERVED) - actingParticipant.getPartySize();
        boolean meetsThreshold = countAfterCancel >= ReservationCapacityPolicy.confirmationThreshold(tableCapacity);

        if (reservation.getRecruitmentStatus() == RecruitmentStatus.CLOSED && !meetsThreshold) {
            return cancelEntireReservation(reservation, actingParticipant, reason, now);
        }

        Map<Long, String> refundStatusByParticipantId = refundAndCancel(
                reservation.getId(), List.of(actingParticipant), actingParticipant.getMemberId(), reason, now);

        if (meetsThreshold) {
            reservation.confirm();
        } else {
            reservation.revertToRecruiting();
        }

        return new ReservationCancellationResponse(
                reservation.getId(), actingParticipant.getId(), ParticipationStatus.CANCELLED,
                CancellationScope.PARTICIPATION, refundStatusByParticipantId.get(actingParticipant.getId()));
    }

    private Map<Long, String> refundAndCancel(
            Long reservationId, List<ReservationParticipant> participants, Long requesterMemberId, String reason, Instant now
    ) {
        List<Long> participantIds = participants.stream().map(ReservationParticipant::getId).toList();
        List<ReservationCancellationRefundPort.RefundRequestResult> results = reservationCancellationRefundPort.requestRefunds(
                new ReservationCancellationRefundPort.RefundRequestCommand(
                        reservationId, participantIds, requesterMemberId, reason));
        participants.forEach(participant -> participant.cancel(reason, now));
        return results.stream().collect(Collectors.toMap(
                ReservationCancellationRefundPort.RefundRequestResult::reservationParticipantId,
                ReservationCancellationRefundPort.RefundRequestResult::refundStatus));
    }

    private void validateReservationNotCancelled(Reservation reservation) {
        if (reservation.isCancelled()) {
            throw new CustomException(ReservationErrorCode.RESERVATION_ALREADY_CANCELLED);
        }
    }

    private void validateCancellationDeadline(Long timeSlotId) {
        Instant startAt = reservationCapacityReader.readTimeSlotStartAt(timeSlotId);
        Instant deadline = startAt.minus(CANCELLATION_DEADLINE);
        if (Instant.now(clock).isAfter(deadline)) {
            throw new CustomException(ReservationErrorCode.CANCELLATION_DEADLINE_PASSED);
        }
    }

    private void validateParticipantCancellable(ReservationParticipant participant) {
        if (participant.getParticipationStatus() == ParticipationStatus.CANCELLED) {
            throw new CustomException(ReservationErrorCode.PARTICIPATION_ALREADY_CANCELLED);
        }
        if (!participant.isCancellable()) {
            throw new CustomException(ReservationErrorCode.CANCELLATION_NOT_ALLOWED);
        }
    }

    /**
     * 본인 참여를 조회한다(Issue #131 오류 계약). Reservation은 생성 시 최초 예약자 참여와 함께
     * 만들어지므로, 이미 조회·잠금에 성공한 Reservation에 참여자가 하나도 없는 경우는 데이터 정합성이
     * 깨진 것이다 — 그 경우에만 {@code PARTICIPATION_NOT_FOUND}를 던지고, 참여자는 있지만 요청자
     * 본인의 참여가 아닌 정상적인 경우는 도메인 공통 {@link CommonErrorCode#ACCESS_DENIED}로 구분한다.
     */
    private ReservationParticipant findParticipantOrThrow(Long reservationId, Long memberId) {
        return reservationParticipantRepository.findByReservationIdAndMemberId(reservationId, memberId)
                .orElseThrow(() -> {
                    if (reservationParticipantRepository.existsByReservationId(reservationId)) {
                        return new CustomException(CommonErrorCode.ACCESS_DENIED);
                    }
                    return new CustomException(ReservationErrorCode.PARTICIPATION_NOT_FOUND);
                });
    }

    private Reservation findReservationWithLockOrThrow(Long reservationId) {
        return reservationRepository.findWithLockById(reservationId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESERVATION_ID_NOT_FOUND));
    }
}
