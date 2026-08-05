package com.bobfull.reservation.controller;

import com.bobfull.common.response.ApiResponse;
import com.bobfull.common.response.PageResponse;
import com.bobfull.common.security.AuthMember;
import com.bobfull.reservation.dto.OwnerReservationDetailResponse;
import com.bobfull.reservation.dto.OwnerReservationParticipantResponse;
import com.bobfull.reservation.service.OwnerReservationQueryService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OWNER의 예약 상세·참여자 목록 조회를 담당한다(Issue #147 §6-12~6-13). */
@RestController
@RequestMapping("/api/owner/reservations/{reservationId}")
public class OwnerReservationController {

    private final OwnerReservationQueryService ownerReservationQueryService;

    public OwnerReservationController(OwnerReservationQueryService ownerReservationQueryService) {
        this.ownerReservationQueryService = ownerReservationQueryService;
    }

    @GetMapping
    public ApiResponse<OwnerReservationDetailResponse> getReservationDetail(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long reservationId
    ) {
        return ApiResponse.success(
                ownerReservationQueryService.getReservationDetail(authMember.id(), reservationId));
    }

    @GetMapping("/participations")
    public ApiResponse<PageResponse<OwnerReservationParticipantResponse>> getParticipants(
            @AuthenticationPrincipal AuthMember authMember,
            @PathVariable Long reservationId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(
                ownerReservationQueryService.getParticipants(authMember.id(), reservationId, pageable));
    }
}
