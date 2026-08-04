package com.bobfull.reservation.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ReservationErrorCode;
import com.bobfull.reservation.dto.ReservationCancellationRequest;
import com.bobfull.reservation.dto.ReservationCancellationResponse;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.port.ReservationCancellationRefundPort;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationCancellationService {
    private final ReservationCancellationTransactionService transactionService;
    private final ReservationCancellationRefundPort reservationCancellationRefundPort;
    private final ReservationRepository reservationRepository;
    private final ReservationParticipantRepository reservationParticipantRepository;

    public ReservationCancellationService(ReservationCancellationTransactionService transactionService,
            ReservationCancellationRefundPort reservationCancellationRefundPort,
            ReservationRepository reservationRepository, ReservationParticipantRepository reservationParticipantRepository) {
        this.transactionService = transactionService;
        this.reservationCancellationRefundPort = reservationCancellationRefundPort;
        this.reservationRepository = reservationRepository;
        this.reservationParticipantRepository = reservationParticipantRepository;
    }

    public ReservationCancellationResponse cancel(Long memberId, Long reservationId, ReservationCancellationRequest request) {
        var acceptance = transactionService.accept(memberId, reservationId, request);
        List<ReservationCancellationRefundPort.RefundRequestResult> results =
                reservationCancellationRefundPort.requestRefunds(acceptance.refundCommand());
        String refundStatus = results.stream().filter(result -> result.reservationParticipantId().equals(acceptance.actingParticipantId()))
                .findFirst().map(ReservationCancellationRefundPort.RefundRequestResult::refundStatus).orElse(null);
        return new ReservationCancellationResponse(acceptance.reservationId(), acceptance.actingParticipantId(),
                ParticipationStatus.CANCEL_REQUESTED, acceptance.scope(), refundStatus);
    }

    /** Reservation을 먼저 잠그고 조건부 UPDATE로 참여자 완료 처리권을 하나만 허용한다. */
    @Transactional
    public void completeParticipantCancellation(Long reservationId, Long reservationParticipantId, Instant completedAt) {
        Reservation reservation = reservationRepository.findWithLockById(reservationId)
                .orElseThrow(() -> new CustomException(ReservationErrorCode.RESERVATION_ID_NOT_FOUND));
        int updatedRows = reservationParticipantRepository.completeCancelIfRequested(reservationParticipantId, completedAt);
        if (updatedRows == 0) return;
        if (reservation.isCancelling()) {
            if (!reservationParticipantRepository.existsByReservationIdAndParticipationStatus(reservationId, ParticipationStatus.CANCEL_REQUESTED)) reservation.cancel();
        } else transactionService.recalculateAfterCompletion(reservation);
    }
}
