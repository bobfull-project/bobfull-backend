package com.bobfull.reservation.dto;

import com.bobfull.paymenttemp.entity.PaymentPurpose;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * type이 CREATE면 targetId는 sessionId(timeSlotId), JOIN이면 targetId는 reservationId다.
 */
public record ReservationPrepareRequest(
        @NotNull PaymentPurpose type,
        @NotNull Long targetId,
        @NotNull @Min(1) Integer partySize
) {
}
