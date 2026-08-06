package com.bobfull.reservation.service;

import com.bobfull.reservation.port.ReservationCancellationRefundPort;
import com.bobfull.reservation.service.ReservationCancellationTransactionService.RecruitmentDeadlineAcceptance;
import com.bobfull.reservation.service.ReservationCancellationTransactionService.RecruitmentDeadlineOutcome;
import org.springframework.stereotype.Service;

/**
 * 모집 마감 기한 도달 후보 하나의 접수, 환불 요청 시작과 결과 이메일 안내를 담당한다(Issue #47, #168).
 * 환불 요청과 이메일 안내 모두 {@code acceptRecruitmentDeadline} 트랜잭션이 이미 커밋된 뒤,
 * 이 논-트랜잭션 메서드 안에서만 호출한다.
 */
@Service
public class RecruitmentDeadlineCancellationService {
    private final ReservationCancellationTransactionService transactionService;
    private final ReservationCancellationRefundPort reservationCancellationRefundPort;
    private final ReservationNotificationService notificationService;

    public RecruitmentDeadlineCancellationService(
            ReservationCancellationTransactionService transactionService,
            ReservationCancellationRefundPort reservationCancellationRefundPort,
            ReservationNotificationService notificationService
    ) {
        this.transactionService = transactionService;
        this.reservationCancellationRefundPort = reservationCancellationRefundPort;
        this.notificationService = notificationService;
    }

    public RecruitmentDeadlineOutcome process(Long reservationId) {
        RecruitmentDeadlineAcceptance acceptance = transactionService.acceptRecruitmentDeadline(reservationId);
        switch (acceptance.outcome()) {
            case CANCELLED -> {
                reservationCancellationRefundPort.requestRefunds(acceptance.refundCommand());
                notificationService.notifyCancelledDueToInsufficientParticipants(
                        reservationId, acceptance.refundCommand().reservationParticipantIds());
            }
            case CLOSED_ONLY -> notificationService.notifyConfirmed(reservationId);
            case ALREADY_PROCESSED -> {
                // 이미 처리된 후보다 — 중복 알림을 막기 위해 이메일도 다시 보내지 않는다(Issue #168 Q2).
            }
        }
        return acceptance.outcome();
    }
}
