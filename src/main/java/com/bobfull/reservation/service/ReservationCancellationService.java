package com.bobfull.reservation.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.reservation.dto.ReservationCancellationRequest;
import com.bobfull.reservation.dto.ReservationCancellationResponse;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationParticipant;
import com.bobfull.reservation.port.ReservationCancellationRefundPort;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증된 MEMBER 본인의 예약 참여 취소를 처리하는 파사드다(Issue #131, #44 최종 계약). 취소 접수는
 * {@link ReservationCancellationTransactionService}의 짧은 잠금 트랜잭션으로 커밋하고, 환불
 * outbound port 호출은 그 트랜잭션이 끝난 뒤 여기서 수행한다 — Reservation 락을 쥔 채 환불을
 * 요청하지 않아 결제 완료 흐름(Payment → Reservation)과의 락 순서 역전을 피한다. 실제 CANCELLED
 * 확정은 환불 완료 후 {@link #completeParticipantCancellation}(PR #137이 호출)이 담당한다.
 */
@Service
public class ReservationCancellationService {

    private final ReservationCancellationTransactionService transactionService;
    private final ReservationCancellationRefundPort reservationCancellationRefundPort;
    private final ReservationRepository reservationRepository;
    private final ReservationParticipantRepository reservationParticipantRepository;

    public ReservationCancellationService(
            ReservationCancellationTransactionService transactionService,
            ReservationCancellationRefundPort reservationCancellationRefundPort,
            ReservationRepository reservationRepository,
            ReservationParticipantRepository reservationParticipantRepository
    ) {
        this.transactionService = transactionService;
        this.reservationCancellationRefundPort = reservationCancellationRefundPort;
        this.reservationRepository = reservationRepository;
        this.reservationParticipantRepository = reservationParticipantRepository;
    }

    /**
     * 취소를 접수하고 환불을 요청한다. 접수 트랜잭션이 커밋된 뒤 환불 포트를 호출하므로, 포트가
     * 예외를 던져도 이미 커밋된 CANCELLING/CANCEL_REQUESTED 상태는 롤백되지 않는다 — 새 계약에서는
     * 이 상태를 "취소 접수 완료, 환불 완료 대기 중"으로 취급하며, 실제 완료·재시도는 #137의 공통
     * 완료 경로와 #141의 정합성 확인 스케줄러가 담당한다.
     */
    public ReservationCancellationResponse cancel(Long memberId, Long reservationId, ReservationCancellationRequest request) {
        ReservationCancellationTransactionService.CancellationAcceptance acceptance =
                transactionService.accept(memberId, reservationId, request);

        List<ReservationCancellationRefundPort.RefundRequestResult> results =
                reservationCancellationRefundPort.requestRefunds(acceptance.refundCommand());
        String refundStatus = results.stream()
                .filter(result -> result.reservationParticipantId().equals(acceptance.actingParticipantId()))
                .findFirst()
                .map(ReservationCancellationRefundPort.RefundRequestResult::refundStatus)
                .orElse(null);

        return new ReservationCancellationResponse(
                acceptance.reservationId(), acceptance.actingParticipantId(),
                ParticipationStatus.CANCEL_REQUESTED, acceptance.scope(), refundStatus);
    }

    /**
     * 취소 접수(CANCEL_REQUESTED)된 참여자 1명의 환불이 완료되어 CANCELLED로 확정한다
     * (Issue #44 완료 진입점, PR #137의 공통 완료 Service가 호출). 이 참여자가 속한 예약이
     * CANCELLING이고 취소 접수된 참여자 전원이 완료됐을 때만 Reservation도 CANCELLED로 확정한다.
     * Payment·Refund 상태 전이는 이 메서드의 책임이 아니다.
     */
    @Transactional
    public void completeParticipantCancellation(Long reservationParticipantId, Instant completedAt) {
        ReservationParticipant participant = reservationParticipantRepository.findById(reservationParticipantId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.PARTICIPATION_ID_NOT_FOUND));
        Reservation reservation = reservationRepository.findWithLockById(participant.getReservationId())
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESERVATION_ID_NOT_FOUND));

        participant.completeCancel(completedAt);

        if (reservation.isCancelling()) {
            boolean anyStillRequested = reservationParticipantRepository.existsByReservationIdAndParticipationStatus(
                    reservation.getId(), ParticipationStatus.CANCEL_REQUESTED);
            if (!anyStillRequested) {
                reservation.cancel();
            }
        }
    }
}
