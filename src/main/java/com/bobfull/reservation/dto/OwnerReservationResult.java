package com.bobfull.reservation.dto;

import com.bobfull.reservation.entity.RecruitmentStatus;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.policy.ReservationCapacityPolicy;
import java.time.Instant;

/** §6-11 식당별 예약 목록 조회 결과 1건이다(Issue #147). */
public record OwnerReservationResult(
        Long reservationId,
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
        return ReservationCapacityPolicy.availableCapacity(capacity, currentParticipantCount, temporaryHeldCount);
    }

    public int confirmationThreshold() {
        return ReservationCapacityPolicy.confirmationThreshold(capacity);
    }
}
