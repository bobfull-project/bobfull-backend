package com.bobfull.payment.service;

import com.bobfull.payment.entity.RefundStatus;
import com.bobfull.reservation.service.ReservationCancellationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 즉시 응답과 웹훅이 같은 예약 완료 경로를 사용하도록 환불 완료를 조정한다. */
@Service
public class RefundCompletionService {
    private final RefundTransactionService transactionService;
    private final ObjectProvider<ReservationCancellationService> cancellationServiceProvider;

    public RefundCompletionService(RefundTransactionService transactionService,
            ObjectProvider<ReservationCancellationService> cancellationServiceProvider) {
        this.transactionService = transactionService;
        this.cancellationServiceProvider = cancellationServiceProvider;
    }

    public RefundTransactionService.RefundCompletion reflectExternalResult(
            Long refundId, String cancellationId, boolean completed) {
        RefundTransactionService.RefundCompletion completion =
                transactionService.reflectExternalResult(refundId, cancellationId, completed);
        completeParticipantIfCompleted(completion);
        return completion;
    }

    public void completeFromWebhook(String cancellationId) {
        transactionService.completeFromWebhook(cancellationId).ifPresent(this::completeParticipantIfCompleted);
    }

    public void markProcessingFromWebhook(String cancellationId) {
        transactionService.markProcessingFromWebhook(cancellationId);
    }

    private void completeParticipantIfCompleted(RefundTransactionService.RefundCompletion completion) {
        if (completion.refundStatus() != RefundStatus.COMPLETED) return;
        cancellationServiceProvider.getObject().completeParticipantCancellation(
                completion.reservationId(), completion.reservationParticipantId(), completion.completedAt());
    }
}
