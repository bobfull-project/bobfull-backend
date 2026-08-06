package com.bobfull.reservation.service;

import com.bobfull.reservation.port.ReservationCancellationRefundPort;
import com.bobfull.reservation.service.ReservationCancellationTransactionService.RecruitmentDeadlineAcceptance;
import com.bobfull.reservation.service.ReservationCancellationTransactionService.RecruitmentDeadlineOutcome;
import org.springframework.stereotype.Service;

/** 모집 마감 기한 도달 후보 하나의 접수와 환불 요청 시작만 담당한다(Issue #47). */
@Service
public class RecruitmentDeadlineCancellationService {
    private final ReservationCancellationTransactionService transactionService;
    private final ReservationCancellationRefundPort reservationCancellationRefundPort;

    public RecruitmentDeadlineCancellationService(
            ReservationCancellationTransactionService transactionService,
            ReservationCancellationRefundPort reservationCancellationRefundPort
    ) {
        this.transactionService = transactionService;
        this.reservationCancellationRefundPort = reservationCancellationRefundPort;
    }

    public RecruitmentDeadlineOutcome process(Long reservationId) {
        RecruitmentDeadlineAcceptance acceptance = transactionService.acceptRecruitmentDeadline(reservationId);
        if (acceptance.outcome() == RecruitmentDeadlineOutcome.CANCELLED) {
            reservationCancellationRefundPort.requestRefunds(acceptance.refundCommand());
        }
        return acceptance.outcome();
    }
}
