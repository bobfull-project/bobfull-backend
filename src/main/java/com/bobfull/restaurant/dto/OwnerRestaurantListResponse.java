package com.bobfull.restaurant.dto;

import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.entity.RestaurantStatus;

public record OwnerRestaurantListResponse(
        Long restaurantId,
        String name,
        String address,
        String category,
        Integer depositPerPerson,
        RestaurantStatus status
) {
    public static OwnerRestaurantListResponse from(Restaurant restaurant) {
        return new OwnerRestaurantListResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.getCategory(),
                restaurant.getDepositPerPerson(),
                restaurant.getStatus()
        );
    }
}
