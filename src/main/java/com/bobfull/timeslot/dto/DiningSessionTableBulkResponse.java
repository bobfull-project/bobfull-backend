package com.bobfull.timeslot.dto;

public record DiningSessionTableBulkResponse(
        Long tableId,
        Integer capacity,
        Integer createdSessionCount
) {
}
