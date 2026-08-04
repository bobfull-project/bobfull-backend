package com.bobfull.payment.adapter;

import com.bobfull.payment.port.PortOneRefundRequester;
import io.portone.sdk.server.PortOneClient;
import io.portone.sdk.server.payment.CancelPaymentResponse;
import io.portone.sdk.server.payment.PaymentCancellation;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.CancelledPayment;
import io.portone.sdk.server.errors.CancelPaymentException;
import java.math.BigDecimal;
import java.util.concurrent.CompletionException;
import org.springframework.stereotype.Component;

@Component
public class PortOneSdkRefundRequester implements PortOneRefundRequester {
    private final PortOneClient portOneClient;

    public PortOneSdkRefundRequester(PortOneClient portOneClient) {
        this.portOneClient = portOneClient;
    }

    @Override
    public RefundResult request(String paymentId, BigDecimal amount, String reason) {
        try {
            CancelPaymentResponse response = portOneClient.getPayment()
                    .cancelPayment(paymentId, amount.longValueExact(), null, null, reason,
                            null, null, null, null, null, null)
                    .join();
            return toRefundResult(response.getCancellation());
        } catch (CompletionException exception) {
            if (containsExplicitCancelFailure(exception)) {
                throw new ExplicitRefundFailureException("PortOne explicitly rejected the refund", exception);
            }
            throw exception;
        }
    }

    private boolean containsExplicitCancelFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof CancelPaymentException) return true;
            current = current.getCause();
        }
        return false;
    }

    static RefundResult toRefundResult(PaymentCancellation cancellation) {
        if (!(cancellation instanceof PaymentCancellation.Recognized recognized)) {
            throw new IllegalStateException("PortOne cancellation response is unrecognized");
        }
        return new RefundResult(recognized.getId(), recognized.getCancelledAt() != null);
    }

    @Override
    public boolean isCancellationCompleted(String paymentId, String cancellationId) {
        io.portone.sdk.server.payment.Payment payment = portOneClient.getPayment().getPayment(paymentId).join();
        java.util.List<? extends PaymentCancellation> cancellations = payment instanceof PaidPayment paid ? paid.getCancellations()
                : payment instanceof CancelledPayment cancelled ? cancelled.getCancellations() : java.util.List.of();
        return cancellations.stream().filter(PaymentCancellation.Recognized.class::isInstance)
                .map(PaymentCancellation.Recognized.class::cast)
                .anyMatch(cancellation -> isCompletedCancellation(cancellation, cancellationId));
    }

    static boolean isCompletedCancellation(PaymentCancellation.Recognized cancellation, String cancellationId) {
        return cancellationId.equals(cancellation.getId()) && cancellation.getCancelledAt() != null;
    }
}
