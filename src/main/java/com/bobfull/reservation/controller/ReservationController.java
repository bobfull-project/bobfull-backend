package com.bobfull.reservation.controller;

import com.bobfull.common.response.ApiResponse;
import com.bobfull.common.security.AuthMember;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.reservation.dto.ReservationAvailabilityResponse;
import com.bobfull.reservation.dto.ReservationPrepareRequest;
import com.bobfull.reservation.dto.ReservationPrepareResponse;
import com.bobfull.reservation.service.ReservationPreparationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationPreparationService reservationPreparationService;

    public ReservationController(ReservationPreparationService reservationPreparationService) {
        this.reservationPreparationService = reservationPreparationService;
    }

    @GetMapping("/availability")
    public ApiResponse<ReservationAvailabilityResponse> checkAvailability(
            @AuthenticationPrincipal AuthMember authMember,
            @RequestParam PaymentPurpose type,
            @RequestParam Long targetId,
            @RequestParam Integer partySize
    ) {
        return ApiResponse.success(
                reservationPreparationService.checkAvailability(authMember.id(), type, targetId, partySize));
    }

    @PostMapping("/prepare")
    public ApiResponse<ReservationPrepareResponse> prepare(
            @AuthenticationPrincipal AuthMember authMember,
            @Valid @RequestBody ReservationPrepareRequest request
    ) {
        return ApiResponse.success(reservationPreparationService.prepare(authMember.id(), request));
    }
}
