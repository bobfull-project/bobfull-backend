package com.bobfull.sharedtable.dto;

import com.bobfull.sharedtable.entity.SharedTable;

public record SharedTableIdResponse(Long tableId) {

    public static SharedTableIdResponse from(SharedTable sharedTable) {
        return new SharedTableIdResponse(sharedTable.getId());
    }
}
