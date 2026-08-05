package com.bobfull.sharedtable.dto;

import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.entity.SharedTableStatus;

public record SharedTableResponse(
        Long tableId,
        Long restaurantId,
        Integer displayNumber,
        Integer capacity,
        SharedTableStatus status
) {
    public static SharedTableResponse from(SharedTable sharedTable) {
        return new SharedTableResponse(
                sharedTable.getId(),
                sharedTable.getRestaurantId(),
                sharedTable.getDisplayNumber(),
                sharedTable.getCapacity(),
                sharedTable.getStatus()
        );
    }
}
