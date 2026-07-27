package com.bobfull.restaurant.controller;

import com.bobfull.common.response.ApiResponse;
import com.bobfull.restaurant.dto.RestaurantDetailResponse;
import com.bobfull.restaurant.service.RestaurantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants")
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @GetMapping("/{restaurantId}")
    public ApiResponse<RestaurantDetailResponse> getRestaurant(@PathVariable Long restaurantId) {
        return ApiResponse.success(restaurantService.getRestaurantDetail(restaurantId));
    }
}
