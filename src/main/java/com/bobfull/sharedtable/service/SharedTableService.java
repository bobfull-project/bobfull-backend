package com.bobfull.sharedtable.service;

import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.RestaurantErrorCode;
import com.bobfull.common.exception.SharedTableErrorCode;
import com.bobfull.common.response.PageResponse;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.repository.RestaurantRepository;
import com.bobfull.sharedtable.dto.SharedTableIdResponse;
import com.bobfull.sharedtable.dto.SharedTableRequest;
import com.bobfull.sharedtable.dto.SharedTableResponse;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import java.time.Clock;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OWNER 합석 테이블 등록·조회·수정·삭제를 담당한다.
 */
@Service
public class SharedTableService {

    private static final Set<Integer> ALLOWED_CAPACITIES = Set.of(2, 4, 6, 8);

    private final SharedTableRepository sharedTableRepository;
    private final RestaurantRepository restaurantRepository;
    private final SharedTableUsageValidator sharedTableUsageValidator;
    private final Clock clock;

    public SharedTableService(
            SharedTableRepository sharedTableRepository,
            RestaurantRepository restaurantRepository,
            SharedTableUsageValidator sharedTableUsageValidator,
            Clock clock
    ) {
        this.sharedTableRepository = sharedTableRepository;
        this.restaurantRepository = restaurantRepository;
        this.sharedTableUsageValidator = sharedTableUsageValidator;
        this.clock = clock;
    }

    @Transactional
    public SharedTableIdResponse register(Long ownerMemberId, Long restaurantId, SharedTableRequest request) {
        Restaurant restaurant = findActiveRestaurantOrThrow(restaurantId);
        validateOwnership(restaurant, ownerMemberId);
        validateCapacity(request.capacity());

        SharedTable sharedTable = SharedTable.create(restaurantId, request.capacity());
        SharedTable savedTable = sharedTableRepository.save(sharedTable);
        return SharedTableIdResponse.from(savedTable);
    }

    @Transactional(readOnly = true)
    public PageResponse<SharedTableResponse> getTables(Long ownerMemberId, Long restaurantId, Pageable pageable) {
        Restaurant restaurant = findActiveRestaurantOrThrow(restaurantId);
        validateOwnership(restaurant, ownerMemberId);

        Page<SharedTable> tables = sharedTableRepository.findAllByRestaurantIdAndDeletedAtIsNull(restaurantId, pageable);
        return PageResponse.from(tables.map(SharedTableResponse::from));
    }

    @Transactional(readOnly = true)
    public SharedTableResponse getTable(Long ownerMemberId, Long tableId) {
        SharedTable sharedTable = findActiveTableOrThrow(tableId);
        validateRestaurantOwnership(sharedTable.getRestaurantId(), ownerMemberId);
        return SharedTableResponse.from(sharedTable);
    }

    @Transactional
    public SharedTableIdResponse update(Long ownerMemberId, Long tableId, SharedTableRequest request) {
        SharedTable sharedTable = findActiveTableOrThrow(tableId);
        validateRestaurantOwnership(sharedTable.getRestaurantId(), ownerMemberId);
        validateCapacity(request.capacity());
        sharedTableUsageValidator.validateCapacityChangeAllowed(sharedTable.getId());

        sharedTable.updateCapacity(request.capacity());
        return SharedTableIdResponse.from(sharedTable);
    }

    @Transactional
    public SharedTableIdResponse delete(Long ownerMemberId, Long tableId) {
        SharedTable sharedTable = findActiveTableOrThrow(tableId);
        validateRestaurantOwnership(sharedTable.getRestaurantId(), ownerMemberId);
        sharedTableUsageValidator.validateDeletionAllowed(sharedTable.getId());

        sharedTable.softDelete(clock.instant());
        return SharedTableIdResponse.from(sharedTable);
    }

    private SharedTable findActiveTableOrThrow(Long tableId) {
        return sharedTableRepository.findByIdAndDeletedAtIsNull(tableId)
                .orElseThrow(() -> new CustomException(SharedTableErrorCode.TABLE_ID_NOT_FOUND));
    }

    private void validateRestaurantOwnership(Long restaurantId, Long ownerMemberId) {
        Restaurant restaurant = findActiveRestaurantOrThrow(restaurantId);
        validateOwnership(restaurant, ownerMemberId);
    }

    private Restaurant findActiveRestaurantOrThrow(Long restaurantId) {
        return restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .orElseThrow(() -> new CustomException(RestaurantErrorCode.RESTAURANT_ID_NOT_FOUND));
    }

    private void validateOwnership(Restaurant restaurant, Long ownerMemberId) {
        if (!restaurant.isOwnedBy(ownerMemberId)) {
            throw new CustomException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    private void validateCapacity(Integer capacity) {
        if (capacity == null || !ALLOWED_CAPACITIES.contains(capacity)) {
            throw new CustomException(SharedTableErrorCode.INVALID_TABLE_CAPACITY);
        }
    }
}
