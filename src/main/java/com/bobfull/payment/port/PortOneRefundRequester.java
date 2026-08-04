package com.bobfull.payment.port;

import java.math.BigDecimal;

/** 외부 SDK의 환불 응답을 결제 도메인에 필요한 최소 정보로 제한한다. */
public interface PortOneRefundRequester {

    RefundResult request(String paymentId, BigDecimal amount, String reason);

    boolean isCancellationCompleted(String paymentId, String cancellationId);

    record RefundResult(String cancellationId, boolean completed) {
    }
}
