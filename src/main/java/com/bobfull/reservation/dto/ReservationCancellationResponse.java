package com.bobfull.reservation.dto;

import com.bobfull.reservation.entity.ParticipationStatus;

public record ReservationCancellationResponse(
        Long reservationId,
        Long participationId,
        ParticipationStatus participationStatus,
        CancellationScope cancellationScope,
        String refundStatus
) {
}
