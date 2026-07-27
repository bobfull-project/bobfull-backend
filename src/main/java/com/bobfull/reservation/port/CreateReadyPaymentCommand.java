package com.bobfull.reservation.port;

import java.math.BigDecimal;

/**
 * 예약 도메인이 검증·계산을 마친 뒤 결제 도메인에 넘기는 READY 결제 생성 요청이다(#35 Q1).
 * 예약 도메인은 Payment 엔티티를 직접 다루지 않고 이 커맨드로 생성 계약만 호출한다.
 */
public record CreateReadyPaymentCommand(
        Long memberId,
        Long timeSlotId,
        Long reservationId,
        PaymentPurpose paymentPurpose,
        int partySize,
        BigDecimal amount
) {
}
