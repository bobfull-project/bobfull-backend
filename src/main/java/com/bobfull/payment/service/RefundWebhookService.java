package com.bobfull.payment.service;

import com.bobfull.payment.port.PortOneRefundRequester;
import org.springframework.stereotype.Service;

@Service
public class RefundWebhookService {
    private final RefundTransactionService transactionService;
    private final PortOneRefundRequester refundRequester;

    public RefundWebhookService(RefundTransactionService transactionService, PortOneRefundRequester refundRequester) {
        this.transactionService = transactionService;
        this.refundRequester = refundRequester;
    }

    public void markProcessing(String cancellationId) { transactionService.markProcessingFromWebhook(cancellationId); }
    public void complete(String paymentId, String cancellationId) {
        if (!refundRequester.isCancellationCompleted(paymentId, cancellationId)) {
            throw new IllegalStateException("PortOne cancellation verification failed");
        }
        transactionService.completeFromWebhook(cancellationId);
    }
}
