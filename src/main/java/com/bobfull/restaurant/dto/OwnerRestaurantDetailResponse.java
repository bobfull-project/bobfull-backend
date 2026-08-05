package com.bobfull.restaurant.dto;

import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.entity.RestaurantStatus;

public record OwnerRestaurantDetailResponse(
        Long restaurantId,
        String name,
        String address,
        String category,
        String description,
        String keyword,
        Integer depositPerPerson,
        RestaurantStatus status,
        String imageUrl
) {
    public static OwnerRestaurantDetailResponse from(Restaurant restaurant) {
        return from(restaurant, null);
    }

    public static OwnerRestaurantDetailResponse from(Restaurant restaurant, String imageUrl) {
        return new OwnerRestaurantDetailResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.getCategory(),
                restaurant.getDescription(),
                restaurant.getKeyword(),
                restaurant.getDepositPerPerson(),
                restaurant.getStatus(),
                imageUrl
        );
    }
}
