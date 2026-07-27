package com.bobfull.reservation.dto;

import com.bobfull.reservation.port.ReadyPaymentResult;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record ReservationPrepareResponse(
        String paymentId,
        String paymentStatus,
        BigDecimal amount,
        OffsetDateTime expiresAt
) {

    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");

    public static ReservationPrepareResponse from(ReadyPaymentResult payment) {
        return new ReservationPrepareResponse(
                payment.paymentId(),
                payment.paymentStatus(),
                payment.amount(),
                payment.expiresAt().atZone(ASIA_SEOUL).toOffsetDateTime()
        );
    }
}
