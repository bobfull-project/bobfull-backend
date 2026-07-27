package com.bobfull.reservation.dto;

import com.bobfull.paymenttemp.entity.Payment;
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

    public static ReservationPrepareResponse from(Payment payment) {
        return new ReservationPrepareResponse(
                payment.getId(),
                payment.getPaymentStatus().name(),
                payment.getAmount(),
                payment.getExpiresAt().atZone(ASIA_SEOUL).toOffsetDateTime()
        );
    }
}
