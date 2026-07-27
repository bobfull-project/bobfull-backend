package com.bobfull.sharedtable.service;

import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.sharedtable.exception.SharedTableErrorCode;
import org.springframework.stereotype.Service;

/**
 * #31 병합 전 #32 1차 구현을 위한 임시 식당 소유권 확인 경계다.
 * 실제 식당 엔티티나 Repository를 소유하지 않으며, #31 병합 후 실제 Restaurant 계약으로 대체해야 한다.
 */
@Service
public class TemporaryRestaurantOwnershipService {

    public void validateOwnedRestaurant(Long restaurantId, Long ownerMemberId) {
        if (restaurantId == null) {
            throw new CustomException(SharedTableErrorCode.RESTAURANT_ID_NOT_FOUND);
        }
        if (ownerMemberId == null) {
            throw new CustomException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
