package com.bobfull.reservation.dto;

import jakarta.validation.constraints.NotBlank;

public record ReservationCancellationRequest(
        @NotBlank String reason
) {
}
