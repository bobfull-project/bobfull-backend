package com.bobfull.timeslot.dto;

import com.bobfull.timeslot.entity.TimeSlot;

public record DiningSessionIdResponse(Long sessionId) {

    public static DiningSessionIdResponse from(TimeSlot timeSlot) {
        return new DiningSessionIdResponse(timeSlot.getId());
    }
}
