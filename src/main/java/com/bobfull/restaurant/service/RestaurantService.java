package com.bobfull.restaurant.service;

import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.ImageErrorCode;
import com.bobfull.common.exception.RestaurantErrorCode;
import com.bobfull.common.response.PageResponse;
import com.bobfull.restaurant.dto.OwnerRestaurantDetailResponse;
import com.bobfull.restaurant.dto.OwnerRestaurantListResponse;
import com.bobfull.restaurant.dto.RestaurantCreateRequest;
import com.bobfull.restaurant.dto.RestaurantDetailResponse;
import com.bobfull.restaurant.dto.RestaurantIdResponse;
import com.bobfull.restaurant.dto.RestaurantSearchRequest;
import com.bobfull.restaurant.dto.RestaurantSearchResponse;
import com.bobfull.restaurant.dto.RestaurantUpdateRequest;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.image.service.RestaurantImageService;
import com.bobfull.restaurant.repository.RestaurantRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * OWNER 식당 등록·조회·수정·삭제와 사용자용 식당 상세 조회를 담당한다.
 * 소유권 대상은 SecurityContext의 인증 사용자 ID로만 결정하며 Request 값을 신뢰하지 않는다.
 */
@Service
public class RestaurantService {

    private static final Logger log = LoggerFactory.getLogger(RestaurantService.class);

    private final RestaurantRepository restaurantRepository;
    private final Clock clock;
    private final RestaurantImageService restaurantImageService;

    public RestaurantService(
            RestaurantRepository restaurantRepository,
            Clock clock,
            RestaurantImageService restaurantImageService
    ) {
        this.restaurantRepository = restaurantRepository;
        this.clock = clock;
        this.restaurantImageService = restaurantImageService;
    }

    @Transactional
    public RestaurantIdResponse register(Long ownerMemberId, RestaurantCreateRequest request) {
        String imageKey = resolveNewImageKey(ownerMemberId, request.imageKey());
        Restaurant restaurant = Restaurant.create(
                ownerMemberId,
                request.name(),
                request.address(),
                request.category(),
                request.description(),
                request.keyword(),
                request.depositPerPerson(),
                imageKey
        );

        Restaurant savedRestaurant = restaurantRepository.save(restaurant);
        return RestaurantIdResponse.from(savedRestaurant);
    }

    @Transactional(readOnly = true)
    public PageResponse<OwnerRestaurantListResponse> getMyRestaurants(Long ownerMemberId, Pageable pageable) {
        Page<Restaurant> restaurants =
                restaurantRepository.findAllByOwnerMemberIdAndDeletedAtIsNull(ownerMemberId, pageable);
        return PageResponse.from(restaurants.map(restaurant ->
                OwnerRestaurantListResponse.from(restaurant, createImageUrl(restaurant))));
    }

    @Transactional(readOnly = true)
    public PageResponse<RestaurantSearchResponse> searchRestaurants(
            RestaurantSearchRequest request,
            Pageable pageable
    ) {
        Page<Restaurant> restaurants = restaurantRepository.search(request, pageable);
        return PageResponse.from(restaurants.map(restaurant ->
                RestaurantSearchResponse.from(restaurant, createImageUrl(restaurant))));
    }

    @Transactional(readOnly = true)
    public OwnerRestaurantDetailResponse getMyRestaurant(Long ownerMemberId, Long restaurantId) {
        Restaurant restaurant = findActiveOrThrow(restaurantId);
        validateOwnership(restaurant, ownerMemberId);
        return OwnerRestaurantDetailResponse.from(restaurant, createImageUrl(restaurant));
    }

    @Transactional
    public RestaurantIdResponse update(Long ownerMemberId, Long restaurantId, RestaurantUpdateRequest request) {
        Restaurant restaurant = findActiveOrThrow(restaurantId);
        validateOwnership(restaurant, ownerMemberId);

        String previousImageKey = restaurant.getImageKey();
        String newImageKey = resolveUpdatedImageKey(
                ownerMemberId,
                restaurant.getId(),
                previousImageKey,
                request.imageKey()
        );
        restaurant.update(request.name(), request.description(), request.keyword(), request.depositPerPerson());
        if (request.imageKey() != null) {
            restaurant.updateImageKey(newImageKey);
            deletePreviousImageAfterCommit(previousImageKey, newImageKey);
        }
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
        return RestaurantDetailResponse.from(restaurant, createImageUrl(restaurant));
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

    private String resolveNewImageKey(Long ownerMemberId, String imageKey) {
        if (imageKey == null) {
            return null;
        }
        restaurantImageService.validateFinalImage(ownerMemberId, imageKey);
        validateUnusedImageKey(imageKey);
        return imageKey;
    }

    private String resolveUpdatedImageKey(
            Long ownerMemberId,
            Long restaurantId,
            String previousImageKey,
            String requestedImageKey
    ) {
        if (requestedImageKey == null) {
            return previousImageKey;
        }
        restaurantImageService.validateFinalImage(ownerMemberId, requestedImageKey);
        validateUnusedImageKeyForUpdate(requestedImageKey, restaurantId);
        return requestedImageKey;
    }

    private void validateUnusedImageKey(String imageKey) {
        if (restaurantRepository.existsByImageKeyAndDeletedAtIsNull(imageKey)) {
            throw new CustomException(ImageErrorCode.RESTAURANT_IMAGE_ALREADY_USED);
        }
    }

    private void validateUnusedImageKeyForUpdate(String imageKey, Long restaurantId) {
        if (restaurantRepository.existsByImageKeyAndIdNotAndDeletedAtIsNull(imageKey, restaurantId)) {
            throw new CustomException(ImageErrorCode.RESTAURANT_IMAGE_ALREADY_USED);
        }
    }

    private String createImageUrl(Restaurant restaurant) {
        return restaurantImageService.createGetUrl(restaurant.getImageKey());
    }

    private void deletePreviousImageAfterCommit(String previousImageKey, String newImageKey) {
        if (!StringUtils.hasText(previousImageKey) || previousImageKey.equals(newImageKey)) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deletePreviousImage(previousImageKey);
                }
            });
            return;
        }
        deletePreviousImage(previousImageKey);
    }

    private void deletePreviousImage(String previousImageKey) {
        if (restaurantRepository.existsByImageKeyAndDeletedAtIsNull(previousImageKey)) {
            log.info("다른 식당이 참조 중인 기존 이미지는 삭제하지 않습니다. imageKey={}", previousImageKey);
            return;
        }
        try {
            restaurantImageService.delete(previousImageKey);
        } catch (RuntimeException exception) {
            log.warn("기존 식당 이미지 삭제에 실패했습니다. imageKey={}", previousImageKey, exception);
        }
    }
}
