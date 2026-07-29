package com.bobfull.timeslot.dto;

import com.bobfull.timeslot.entity.TimeSlot;
import java.time.OffsetDateTime;

public record DiningSessionResponse(
        Long sessionId,
        Long tableId,
        Integer capacity,
        OffsetDateTime startAt,
        OffsetDateTime endAt
) {
    public static DiningSessionResponse of(
            TimeSlot timeSlot,
            Integer capacity,
            OffsetDateTime startAt,
            OffsetDateTime endAt
    ) {
        return new DiningSessionResponse(
                timeSlot.getId(),
                timeSlot.getSharedTableId(),
                capacity,
                startAt,
                endAt
        );
    }
}
