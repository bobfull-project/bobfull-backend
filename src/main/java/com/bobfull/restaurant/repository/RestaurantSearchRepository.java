package com.bobfull.restaurant.repository;

import com.bobfull.restaurant.dto.RestaurantSearchRequest;
import com.bobfull.restaurant.entity.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RestaurantSearchRepository {

    Page<Restaurant> search(RestaurantSearchRequest request, Pageable pageable);
}
