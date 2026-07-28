package com.bobfull.timeslot.dto;

import com.bobfull.timeslot.entity.TimeSlot;
import java.time.OffsetDateTime;

public record AvailableDiningSessionResponse(
        Long sessionId,
        Long tableId,
        Integer capacity,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        Integer availableCapacity
) {
    public static AvailableDiningSessionResponse of(
            TimeSlot timeSlot,
            Integer capacity,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            Integer availableCapacity
    ) {
        return new AvailableDiningSessionResponse(
                timeSlot.getId(),
                timeSlot.getSharedTableId(),
                capacity,
                startAt,
                endAt,
                availableCapacity
        );
    }
}
