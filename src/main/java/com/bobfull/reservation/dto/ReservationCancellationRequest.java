package com.bobfull.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReservationCancellationRequest(
        @NotBlank @Size(max = 255) String reason
) {
}
