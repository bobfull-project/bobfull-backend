package com.bobfull.reservation.port;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * READY 결제 생성 계약의 응답이다. paymentStatus는 결제 도메인 내부 Enum을 그대로 노출하지
 * 않기 위해 문자열로만 전달한다(#91 병합 전까지 이 계약의 실제 값은 항상 "READY"다).
 */
public record ReadyPaymentResult(
        String paymentId,
        String paymentStatus,
        BigDecimal amount,
        Instant expiresAt
) {
}
