package com.bobfull.restaurant.service;

import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.exception.RestaurantErrorCode;
import com.bobfull.restaurant.repository.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * #31 병합 전 #32 1차 구현을 위한 임시 식당 소유권 확인 서비스다.
 * 실제 식당 관리 계약이 병합되면 이 클래스는 실제 Restaurant 서비스 계약으로 대체해야 한다.
 */
@Service
public class TemporaryRestaurantOwnershipService {

    private final RestaurantRepository restaurantRepository;

    public TemporaryRestaurantOwnershipService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    @Transactional(readOnly = true)
    public Restaurant getOwnedRestaurant(Long restaurantId, Long ownerMemberId) {
        Restaurant restaurant = restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .orElseThrow(() -> new CustomException(RestaurantErrorCode.RESTAURANT_ID_NOT_FOUND));

        if (!restaurant.getOwnerMemberId().equals(ownerMemberId)) {
            throw new CustomException(CommonErrorCode.ACCESS_DENIED);
        }

        return restaurant;
    }
}
