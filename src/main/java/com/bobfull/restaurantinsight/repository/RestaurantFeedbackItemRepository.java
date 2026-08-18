package com.bobfull.restaurantinsight.repository;
import com.bobfull.restaurantinsight.entity.RestaurantFeedbackItem;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RestaurantFeedbackItemRepository extends JpaRepository<RestaurantFeedbackItem, Long> { }
