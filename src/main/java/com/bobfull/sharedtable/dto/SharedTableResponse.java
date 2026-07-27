package com.bobfull.sharedtable.dto;

import com.bobfull.sharedtable.entity.SharedTable;

public record SharedTableResponse(
        Long tableId,
        Long restaurantId,
        int capacity,
        String status
) {

    public static SharedTableResponse from(SharedTable sharedTable) {
        return new SharedTableResponse(
                sharedTable.getId(),
                sharedTable.getRestaurant().getId(),
                sharedTable.getCapacity(),
                sharedTable.getStatus().name()
        );
    }
}
