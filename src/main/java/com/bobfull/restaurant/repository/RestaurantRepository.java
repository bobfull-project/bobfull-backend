package com.bobfull.restaurant.repository;

import com.bobfull.restaurant.entity.Restaurant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    Optional<Restaurant> findByIdAndDeletedAtIsNull(Long id);
}
