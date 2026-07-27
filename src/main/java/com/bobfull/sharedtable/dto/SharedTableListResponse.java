package com.bobfull.sharedtable.dto;

import com.bobfull.sharedtable.entity.SharedTable;
import java.util.List;
import org.springframework.data.domain.Page;

public record SharedTableListResponse(
        List<SharedTableResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static SharedTableListResponse from(Page<SharedTable> sharedTables) {
        return new SharedTableListResponse(
                sharedTables.getContent().stream()
                        .map(SharedTableResponse::from)
                        .toList(),
                sharedTables.getNumber(),
                sharedTables.getSize(),
                sharedTables.getTotalElements(),
                sharedTables.getTotalPages()
        );
    }
}
