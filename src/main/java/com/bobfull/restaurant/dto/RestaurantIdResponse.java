package com.bobfull.restaurant.dto;

import com.bobfull.restaurant.entity.Restaurant;

public record RestaurantIdResponse(Long restaurantId) {

    public static RestaurantIdResponse from(Restaurant restaurant) {
        return new RestaurantIdResponse(restaurant.getId());
    }
}
