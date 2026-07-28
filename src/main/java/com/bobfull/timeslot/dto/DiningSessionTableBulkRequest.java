package com.bobfull.timeslot.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record DiningSessionTableBulkRequest(
        @NotEmpty List<@NotNull LocalDate> dates,
        @NotNull Integer capacity,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotNull @Positive Integer intervalMinutes
) {
}
