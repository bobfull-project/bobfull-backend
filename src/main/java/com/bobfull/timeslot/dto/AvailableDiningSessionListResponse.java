package com.bobfull.timeslot.dto;

import java.util.List;

public record AvailableDiningSessionListResponse(
        Long restaurantId,
        List<AvailableDiningSessionResponse> content
) {
}
