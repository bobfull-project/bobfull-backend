package com.bobfull.admin.dto;

public record AdminOverviewStatisticsResponse(
        long totalReservationCount,
        double reservationConfirmationRate,
        double noShowRate
) {
}
