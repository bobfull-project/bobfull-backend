package com.bobfull.restaurant.dto;

import com.bobfull.restaurant.entity.Restaurant;

public record RestaurantSearchResponse(
        Long restaurantId,
        String name,
        String address,
        String category,
        String keyword,
        Integer depositPerPerson
) {
    public static RestaurantSearchResponse from(Restaurant restaurant) {
        return new RestaurantSearchResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.getCategory(),
                restaurant.getKeyword(),
                restaurant.getDepositPerPerson()
        );
    }
}
