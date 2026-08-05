package com.bobfull.admin.dto;

public record AdminRestaurantStatisticsResponse(
        Long restaurantId,
        String restaurantName,
        long totalReservationCount,
        long confirmedReservationCount,
        double confirmationRate
) {
    public static AdminRestaurantStatisticsResponse of(AdminRestaurantStatisticsResult result, double confirmationRate) {
        return new AdminRestaurantStatisticsResponse(
                result.restaurantId(), result.restaurantName(),
                result.totalReservationCount(), result.confirmedReservationCount(), confirmationRate);
    }
}
