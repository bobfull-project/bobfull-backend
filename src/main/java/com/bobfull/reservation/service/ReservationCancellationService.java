package com.bobfull.reservation.service;

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
        validateCancellationDeadline(reservation.getTimeSlotId());

        ReservationParticipant actingParticipant = findParticipantOrThrow(reservationId, memberId);
        validateParticipantCancellable(actingParticipant);

        Instant now = Instant.now(clock);
        String reason = request.reason();

        if (reservation.isCreatedBy(memberId)) {
            return cancelByCreator(reservation, actingParticipant, reason, now);
        }
        return cancelByParticipant(reservation, actingParticipant, reason, now);
    }

    private ReservationCancellationResponse cancelByCreator(
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

    private ReservationCancellationResponse cancelByParticipant(
            Reservation reservation, ReservationParticipant actingParticipant, String reason, Instant now
    ) {
        Map<Long, String> refundStatusByParticipantId = refundAndCancel(
                reservation.getId(), List.of(actingParticipant), actingParticipant.getMemberId(), reason, now);
        recalculateAfterParticipationCancel(reservation, actingParticipant.getMemberId(), reason, now);

        return new ReservationCancellationResponse(
                reservation.getId(), actingParticipant.getId(), ParticipationStatus.CANCELLED,
                CancellationScope.PARTICIPATION, refundStatusByParticipantId.get(actingParticipant.getId()));
    }

    /**
     * 추가 참여자 취소 후 인원·상태를 재계산한다(§0.8, PROJECT_CONTEXT 취소·환불 절).
     * 모집 OPEN이면 확정 기준 도달 여부로 RECRUITING/CONFIRMED를 결정하고, 모집 CLOSED에서
     * 기준 미달이 되면 임의로 재오픈하지 않고 남은 유효 참여자까지 전액 환불하며 예약 전체를 취소한다.
     */
    private void recalculateAfterParticipationCancel(
            Reservation reservation, Long requesterMemberId, String reason, Instant now
    ) {
        int tableCapacity = reservationCapacityReader.readTableCapacity(reservation.getTimeSlotId());
        int currentParticipantCount = reservationParticipantRepository.sumPartySize(
                reservation.getId(), ParticipationStatus.RESERVED);
        boolean meetsThreshold = currentParticipantCount >= ReservationCapacityPolicy.confirmationThreshold(tableCapacity);

        if (reservation.getRecruitmentStatus() == RecruitmentStatus.CLOSED) {
            if (!meetsThreshold) {
                List<ReservationParticipant> remaining = reservationParticipantRepository
                        .findAllByReservationIdAndParticipationStatus(reservation.getId(), ParticipationStatus.RESERVED);
                if (!remaining.isEmpty()) {
                    refundAndCancel(reservation.getId(), remaining, requesterMemberId, reason, now);
                }
                reservation.cancel();
            }
            return;
        }

        if (meetsThreshold) {
            reservation.confirm();
        } else {
            reservation.revertToRecruiting();
        }
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

    private ReservationParticipant findParticipantOrThrow(Long reservationId, Long memberId) {
        return reservationParticipantRepository.findByReservationIdAndMemberId(reservationId, memberId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.PARTICIPATION_NOT_FOUND));
    }

    private Reservation findReservationWithLockOrThrow(Long reservationId) {
        return reservationRepository.findWithLockById(reservationId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESERVATION_ID_NOT_FOUND));
    }
}
