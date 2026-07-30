package com.bobfull.restaurant.repository;

import com.bobfull.restaurant.entity.Restaurant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long>, RestaurantSearchRepository {

    Optional<Restaurant> findByIdAndDeletedAtIsNull(Long id);

    Page<Restaurant> findAllByOwnerMemberIdAndDeletedAtIsNull(Long ownerMemberId, Pageable pageable);
}
