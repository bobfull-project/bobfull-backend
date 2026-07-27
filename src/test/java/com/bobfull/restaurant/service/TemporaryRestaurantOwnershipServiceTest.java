package com.bobfull.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.exception.RestaurantErrorCode;
import com.bobfull.restaurant.repository.RestaurantRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemporaryRestaurantOwnershipServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private TemporaryRestaurantOwnershipService temporaryRestaurantOwnershipService;

    @Test
    void 본인_식당이면_식당을_반환한다() {
        // given
        Restaurant restaurant = Restaurant.createTemporary(1L);
        when(restaurantRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(restaurant));

        // when
        Restaurant result = temporaryRestaurantOwnershipService.getOwnedRestaurant(10L, 1L);

        // then
        assertThat(result).isSameAs(restaurant);
    }

    @Test
    void 식당이_없으면_RESTAURANT_ID_NOT_FOUND를_반환한다() {
        // given
        when(restaurantRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.empty());

        // when
        Throwable result = catchThrowable(
                () -> temporaryRestaurantOwnershipService.getOwnedRestaurant(10L, 1L));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(RestaurantErrorCode.RESTAURANT_ID_NOT_FOUND);
    }

    @Test
    void 타인_식당이면_ACCESS_DENIED를_반환한다() {
        // given
        Restaurant restaurant = Restaurant.createTemporary(2L);
        when(restaurantRepository.findByIdAndDeletedAtIsNull(10L))
                .thenReturn(Optional.of(restaurant));

        // when
        Throwable result = catchThrowable(
                () -> temporaryRestaurantOwnershipService.getOwnedRestaurant(10L, 1L));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }
}
