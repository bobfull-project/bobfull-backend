package com.bobfull.sharedtable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.sharedtable.exception.SharedTableErrorCode;
import org.junit.jupiter.api.Test;

class TemporaryRestaurantOwnershipServiceTest {

    private final TemporaryRestaurantOwnershipService temporaryRestaurantOwnershipService =
            new TemporaryRestaurantOwnershipService();

    @Test
    void 임시_소유권_스텁은_식당_엔티티를_조회하지_않고_검증_경계만_제공한다() {
        // when
        Throwable result = catchThrowable(
                () -> temporaryRestaurantOwnershipService.validateOwnedRestaurant(10L, 1L));

        // then
        assertThat(result).isNull();
    }

    @Test
    void 식당_ID가_없으면_RESTAURANT_ID_NOT_FOUND를_반환한다() {
        // when
        Throwable result = catchThrowable(
                () -> temporaryRestaurantOwnershipService.validateOwnedRestaurant(null, 1L));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(SharedTableErrorCode.RESTAURANT_ID_NOT_FOUND);
    }

    @Test
    void 인증_회원_ID가_없으면_ACCESS_DENIED를_반환한다() {
        // when
        Throwable result = catchThrowable(
                () -> temporaryRestaurantOwnershipService.validateOwnedRestaurant(10L, null));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }
}
