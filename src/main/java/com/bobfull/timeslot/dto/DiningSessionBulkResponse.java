package com.bobfull.timeslot.dto;

public record DiningSessionBulkResponse(
        Long tableId,
        Integer createdSessionCount
) {
}
