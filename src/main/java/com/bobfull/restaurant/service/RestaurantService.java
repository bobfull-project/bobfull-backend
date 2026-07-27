package com.bobfull.restaurant.service;

import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.RestaurantErrorCode;
import com.bobfull.common.response.PageResponse;
import com.bobfull.restaurant.dto.OwnerRestaurantDetailResponse;
import com.bobfull.restaurant.dto.OwnerRestaurantListResponse;
import com.bobfull.restaurant.dto.RestaurantCreateRequest;
import com.bobfull.restaurant.dto.RestaurantDetailResponse;
import com.bobfull.restaurant.dto.RestaurantIdResponse;
import com.bobfull.restaurant.dto.RestaurantUpdateRequest;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.repository.RestaurantRepository;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OWNER 식당 등록·조회·수정·삭제와 사용자용 식당 상세 조회를 담당한다.
 * 소유권 대상은 SecurityContext의 인증 사용자 ID로만 결정하며 Request 값을 신뢰하지 않는다.
 */
@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final Clock clock;

    public RestaurantService(RestaurantRepository restaurantRepository, Clock clock) {
        this.restaurantRepository = restaurantRepository;
        this.clock = clock;
    }

    @Transactional
    public RestaurantIdResponse register(Long ownerMemberId, RestaurantCreateRequest request) {
        Restaurant restaurant = Restaurant.create(
                ownerMemberId,
                request.name(),
                request.address(),
                request.category(),
                request.description(),
                request.keyword(),
                request.depositPerPerson()
        );

        Restaurant savedRestaurant = restaurantRepository.save(restaurant);
        return RestaurantIdResponse.from(savedRestaurant);
    }

    @Transactional(readOnly = true)
    public PageResponse<OwnerRestaurantListResponse> getMyRestaurants(Long ownerMemberId, Pageable pageable) {
        Page<Restaurant> restaurants =
                restaurantRepository.findAllByOwnerMemberIdAndDeletedAtIsNull(ownerMemberId, pageable);
        return PageResponse.from(restaurants.map(OwnerRestaurantListResponse::from));
    }

    @Transactional(readOnly = true)
    public OwnerRestaurantDetailResponse getMyRestaurant(Long ownerMemberId, Long restaurantId) {
        Restaurant restaurant = findActiveOrThrow(restaurantId);
        validateOwnership(restaurant, ownerMemberId);
        return OwnerRestaurantDetailResponse.from(restaurant);
    }

    @Transactional
    public RestaurantIdResponse update(Long ownerMemberId, Long restaurantId, RestaurantUpdateRequest request) {
        Restaurant restaurant = findActiveOrThrow(restaurantId);
        validateOwnership(restaurant, ownerMemberId);

        restaurant.update(request.name(), request.description(), request.keyword(), request.depositPerPerson());
        return RestaurantIdResponse.from(restaurant);
    }

    @Transactional
    public RestaurantIdResponse delete(Long ownerMemberId, Long restaurantId) {
        Restaurant restaurant = findActiveOrThrow(restaurantId);
        validateOwnership(restaurant, ownerMemberId);

        // 합석 테이블·회차·예약 도메인이 아직 없어 연결 데이터 검사를 하지 않는다.
        // 해당 도메인 구현 시 활성 데이터가 있으면 여기서 RestaurantErrorCode.RESTAURANT_DELETE_NOT_ALLOWED를 던져야 한다(Issue #31 결정 2).
        restaurant.softDelete(clock.instant());
        return RestaurantIdResponse.from(restaurant);
    }

    @Transactional(readOnly = true)
    public RestaurantDetailResponse getRestaurantDetail(Long restaurantId) {
        Restaurant restaurant = findActiveOrThrow(restaurantId);
        return RestaurantDetailResponse.from(restaurant);
    }

    private Restaurant findActiveOrThrow(Long restaurantId) {
        return restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .orElseThrow(() -> new CustomException(RestaurantErrorCode.RESTAURANT_ID_NOT_FOUND));
    }

    private void validateOwnership(Restaurant restaurant, Long ownerMemberId) {
        if (!restaurant.isOwnedBy(ownerMemberId)) {
            throw new CustomException(CommonErrorCode.ACCESS_DENIED);
        }
    }
}
