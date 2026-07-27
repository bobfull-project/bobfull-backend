package com.bobfull.sharedtable.controller;

import com.bobfull.common.response.ApiResponse;
import com.bobfull.common.security.AuthMember;
import com.bobfull.sharedtable.dto.SharedTableCreateRequest;
import com.bobfull.sharedtable.dto.SharedTableIdResponse;
import com.bobfull.sharedtable.dto.SharedTableListResponse;
import com.bobfull.sharedtable.dto.SharedTableResponse;
import com.bobfull.sharedtable.dto.SharedTableUpdateRequest;
import com.bobfull.sharedtable.service.SharedTableService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner")
public class SharedTableController {

    private final SharedTableService sharedTableService;

    public SharedTableController(SharedTableService sharedTableService) {
        this.sharedTableService = sharedTableService;
    }

    @PostMapping("/restaurants/{restaurantId}/tables")
    public ResponseEntity<ApiResponse<SharedTableIdResponse>> create(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long restaurantId,
            @Valid @RequestBody SharedTableCreateRequest request
    ) {
        SharedTableIdResponse response = sharedTableService.create(authMember.id(), restaurantId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/restaurants/{restaurantId}/tables")
    public ApiResponse<SharedTableListResponse> getTables(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long restaurantId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        SharedTableListResponse response = sharedTableService.getTables(authMember.id(), restaurantId, pageable);

        return ApiResponse.success(response);
    }

    @GetMapping("/tables/{tableId}")
    public ApiResponse<SharedTableResponse> getTable(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long tableId
    ) {
        SharedTableResponse response = sharedTableService.getTable(authMember.id(), tableId);

        return ApiResponse.success(response);
    }

    @PatchMapping("/tables/{tableId}")
    public ApiResponse<SharedTableIdResponse> update(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long tableId,
            @Valid @RequestBody SharedTableUpdateRequest request
    ) {
        SharedTableIdResponse response = sharedTableService.update(authMember.id(), tableId, request);

        return ApiResponse.success(response);
    }

    @DeleteMapping("/tables/{tableId}")
    public ApiResponse<SharedTableIdResponse> delete(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long tableId
    ) {
        SharedTableIdResponse response = sharedTableService.delete(authMember.id(), tableId);

        return ApiResponse.success(response);
    }
}
