package com.bobfull.payment.service;

import com.bobfull.payment.entity.Refund;
import com.bobfull.payment.port.PortOneRefundRequester;
import org.springframework.stereotype.Service;

/** PortOne 조회 결과를 기존 환불 완료 경로에 안전하게 연결한다. */
@Service
public class RefundReconciliationProcessor {
    private final PortOneRefundRequester refundRequester;
    private final RefundTransactionService transactionService;
    private final RefundCompletionService completionService;

    public RefundReconciliationProcessor(PortOneRefundRequester refundRequester,
                                         RefundTransactionService transactionService,
                                         RefundCompletionService completionService) {
        this.refundRequester = refundRequester;
        this.transactionService = transactionService;
        this.completionService = completionService;
    }

    public PortOneRefundRequester.ReconciliationResult reconcile(Refund refund) {
        try {
            PortOneRefundRequester.ReconciliationResult result = refundRequester.reconcile(
                    refund.getPayment().getPaymentId(), refund.getCancellationId(), refund.getAmount(), refund.getRequestedAt());
            if (result.status() == PortOneRefundRequester.ReconciliationStatus.COMPLETED) {
                completionService.reflectExternalResult(refund.getId(), result.cancellationId(), true);
            } else if (result.status() == PortOneRefundRequester.ReconciliationStatus.PROCESSING
                    && result.cancellationId() != null) {
                completionService.reflectExternalResult(refund.getId(), result.cancellationId(), false);
            }
            return result;
        } finally {
            // 조회 결과와 상태 변경 실패 여부와 무관하게, 실제 PG 조회 시도는 기록한다.
            transactionService.markPgChecked(refund.getId());
        }
    }
}
