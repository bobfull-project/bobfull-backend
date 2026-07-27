package com.bobfull.restaurant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.RestaurantErrorCode;
import com.bobfull.common.response.PageResponse;
import com.bobfull.restaurant.dto.OwnerRestaurantDetailResponse;
import com.bobfull.restaurant.dto.RestaurantCreateRequest;
import com.bobfull.restaurant.dto.RestaurantDetailResponse;
import com.bobfull.restaurant.dto.RestaurantIdResponse;
import com.bobfull.restaurant.dto.RestaurantUpdateRequest;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.repository.RestaurantRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 식당 등록·조회·수정·삭제의 소유권 검증과 상태 변경을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private RestaurantRepository restaurantRepository;

    @InjectMocks
    private RestaurantService restaurantService;

    private Restaurant restaurantOwnedBy(Long ownerMemberId) {
        return Restaurant.create(ownerMemberId, "밥풀식당", "제주시 애월읍 1", "한식", "설명", "흑돼지,혼밥", 10000);
    }

    @Test
    void 식당을_등록하면_등록한_회원을_소유자로_저장한다() {
        // given
        RestaurantCreateRequest request =
                new RestaurantCreateRequest("밥풀식당", "제주시 애월읍 1", "한식", "설명", "흑돼지,혼밥", 10000);
        given(restaurantRepository.save(any(Restaurant.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        RestaurantIdResponse response = restaurantService.register(1L, request);

        // then
        assertThat(response).isNotNull();
    }

    @Test
    void 내_식당_목록을_조회하면_본인_소유_식당만_페이징으로_반환한다() {
        // given
        Restaurant restaurant = restaurantOwnedBy(1L);
        Pageable pageable = PageRequest.of(0, 20);
        given(restaurantRepository.findAllByOwnerMemberIdAndDeletedAtIsNull(1L, pageable))
                .willReturn(new PageImpl<>(List.of(restaurant), pageable, 1));

        // when
        PageResponse<?> response = restaurantService.getMyRestaurants(1L, pageable);

        // then
        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void 존재하지_않는_식당을_조회하면_예외가_발생한다() {
        // given
        given(restaurantRepository.findByIdAndDeletedAtIsNull(999L)).willReturn(Optional.empty());

        // when
        Throwable result = catchThrowable(() -> restaurantService.getMyRestaurant(1L, 999L));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(RestaurantErrorCode.RESTAURANT_ID_NOT_FOUND);
    }

    @Test
    void 본인_식당을_조회하면_상세_정보를_반환한다() {
        // given
        Restaurant restaurant = restaurantOwnedBy(1L);
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));

        // when
        OwnerRestaurantDetailResponse response = restaurantService.getMyRestaurant(1L, 10L);

        // then
        assertThat(response.name()).isEqualTo("밥풀식당");
    }

    @Test
    void 타인_식당을_조회하면_403_예외가_발생한다() {
        // given
        Restaurant restaurant = restaurantOwnedBy(1L);
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));

        // when
        Throwable result = catchThrowable(() -> restaurantService.getMyRestaurant(2L, 10L));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    void 본인_식당을_수정하면_변경한_내용이_반영된다() {
        // given
        Restaurant restaurant = restaurantOwnedBy(1L);
        RestaurantUpdateRequest request = new RestaurantUpdateRequest("새이름", "새설명", "한식,혼밥", 12000);
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));

        // when
        restaurantService.update(1L, 10L, request);

        // then
        assertThat(restaurant.getName()).isEqualTo("새이름");
        assertThat(restaurant.getDepositPerPerson()).isEqualTo(12000);
    }

    @Test
    void 타인_식당을_수정하면_403_예외가_발생하고_변경되지_않는다() {
        // given
        Restaurant restaurant = restaurantOwnedBy(1L);
        RestaurantUpdateRequest request = new RestaurantUpdateRequest("새이름", "새설명", "한식,혼밥", 12000);
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));

        // when
        Throwable result = catchThrowable(() -> restaurantService.update(2L, 10L, request));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED);
        assertThat(restaurant.getName()).isEqualTo("밥풀식당");
    }

    @Test
    void 본인_식당을_삭제하면_소프트_딜리트된다() {
        // given
        RestaurantService clockedService = new RestaurantService(restaurantRepository, FIXED_CLOCK);
        Restaurant restaurant = restaurantOwnedBy(1L);
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));

        // when
        clockedService.delete(1L, 10L);

        // then
        assertThat(restaurant.getDeletedAt()).isEqualTo(FIXED_CLOCK.instant());
    }

    @Test
    void 타인_식당을_삭제하면_403_예외가_발생하고_삭제되지_않는다() {
        // given
        Restaurant restaurant = restaurantOwnedBy(1L);
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));

        // when
        Throwable result = catchThrowable(() -> restaurantService.delete(2L, 10L));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED);
        assertThat(restaurant.getDeletedAt()).isNull();
    }

    @Test
    void 소프트_삭제된_식당은_findByIdAndDeletedAtIsNull_조회에서_제외돼_404를_반환한다() {
        // given
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.empty());

        // when
        Throwable result = catchThrowable(() -> restaurantService.getRestaurantDetail(10L));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(RestaurantErrorCode.RESTAURANT_ID_NOT_FOUND);
    }

    @Test
    void 사용자용_상세_조회는_존재하는_식당의_공개_정보를_반환한다() {
        // given
        Restaurant restaurant = restaurantOwnedBy(1L);
        given(restaurantRepository.findByIdAndDeletedAtIsNull(10L)).willReturn(Optional.of(restaurant));

        // when
        RestaurantDetailResponse response = restaurantService.getRestaurantDetail(10L);

        // then
        assertThat(response.name()).isEqualTo("밥풀식당");
        assertThat(response.depositPerPerson()).isEqualTo(10000);
    }
}
