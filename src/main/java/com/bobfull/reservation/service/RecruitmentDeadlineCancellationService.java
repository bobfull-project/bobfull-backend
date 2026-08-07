package com.bobfull.reservation.service;

import com.bobfull.reservation.port.ReservationCancellationRefundPort;
import com.bobfull.reservation.service.ReservationCancellationTransactionService.RecruitmentDeadlineAcceptance;
import com.bobfull.reservation.service.ReservationCancellationTransactionService.RecruitmentDeadlineOutcome;
import org.springframework.stereotype.Service;

/**
 * 모집 마감 기한 도달 후보 하나의 접수와 환불 요청 시작을 담당한다(Issue #47).
 * 결과 이메일 안내는 이 서비스가 직접 호출하지 않는다 — {@code acceptRecruitmentDeadline}이
 * 발행하는 {@code RecruitmentDeadlineConfirmedEvent}/{@code RecruitmentDeadlineCancelledEvent}를
 * 별도 AFTER_COMMIT 리스너가 구독해 처리한다(Issue #168 V2). 그 결과, 환불 요청이 실패해도
 * 이미 커밋 시점에 발행된 이메일 이벤트 처리에는 영향을 주지 않는다 — 두 후속 작업이
 * 서로 독립적으로 실행된다.
 */
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
