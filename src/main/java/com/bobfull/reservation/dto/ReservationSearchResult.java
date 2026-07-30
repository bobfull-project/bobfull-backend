package com.bobfull.reservation.dto;

import com.bobfull.reservation.entity.RecruitmentStatus;
import com.bobfull.reservation.entity.ReservationStatus;
import java.time.Instant;

public record ReservationSearchResult(
        Long reservationId,
        Long restaurantId,
        String restaurantName,
        Long sessionId,
        Long tableId,
        Integer capacity,
        Instant startAt,
        Instant endAt,
        ReservationStatus reservationStatus,
        RecruitmentStatus recruitmentStatus,
        Long currentParticipantCount,
        Long temporaryHeldCount
) {
    public int availableCapacity() {
        return Math.max(0, capacity - currentParticipantCount.intValue() - temporaryHeldCount.intValue());
    }

    public int confirmationThreshold() {
        return capacity == 2 ? 2 : capacity - 1;
    }
}
