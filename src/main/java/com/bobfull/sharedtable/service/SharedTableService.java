package com.bobfull.sharedtable.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.sharedtable.dto.SharedTableCreateRequest;
import com.bobfull.sharedtable.dto.SharedTableIdResponse;
import com.bobfull.sharedtable.dto.SharedTableListResponse;
import com.bobfull.sharedtable.dto.SharedTableResponse;
import com.bobfull.sharedtable.dto.SharedTableUpdateRequest;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.exception.SharedTableErrorCode;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharedTableService {

    private static final Set<Integer> ALLOWED_CAPACITIES = Set.of(2, 4, 6, 8);

    private final SharedTableRepository sharedTableRepository;
    private final TemporaryRestaurantOwnershipService restaurantOwnershipService;
    private final Clock clock;

    public SharedTableService(
            SharedTableRepository sharedTableRepository,
            TemporaryRestaurantOwnershipService restaurantOwnershipService,
            Clock clock
    ) {
        this.sharedTableRepository = sharedTableRepository;
        this.restaurantOwnershipService = restaurantOwnershipService;
        this.clock = clock;
    }

    @Transactional
    public SharedTableIdResponse create(Long ownerMemberId, Long restaurantId, SharedTableCreateRequest request) {
        validateCapacity(request.capacity());
        restaurantOwnershipService.validateOwnedRestaurant(restaurantId, ownerMemberId);

        SharedTable sharedTable = SharedTable.create(restaurantId, request.capacity());
        SharedTable savedSharedTable = sharedTableRepository.save(sharedTable);

        return new SharedTableIdResponse(savedSharedTable.getId());
    }

    @Transactional(readOnly = true)
    public SharedTableListResponse getTables(Long ownerMemberId, Long restaurantId, Pageable pageable) {
        restaurantOwnershipService.validateOwnedRestaurant(restaurantId, ownerMemberId);

        Page<SharedTable> sharedTables =
                sharedTableRepository.findAllByRestaurantIdAndDeletedAtIsNull(restaurantId, pageable);

        return SharedTableListResponse.from(sharedTables);
    }

    @Transactional(readOnly = true)
    public SharedTableResponse getTable(Long ownerMemberId, Long tableId) {
        SharedTable sharedTable = getOwnedSharedTable(ownerMemberId, tableId);

        return SharedTableResponse.from(sharedTable);
    }

    @Transactional
    public SharedTableIdResponse update(Long ownerMemberId, Long tableId, SharedTableUpdateRequest request) {
        validateCapacity(request.capacity());
        SharedTable sharedTable = getOwnedSharedTable(ownerMemberId, tableId);

        sharedTable.updateCapacity(request.capacity());

        return new SharedTableIdResponse(sharedTable.getId());
    }

    @Transactional
    public SharedTableIdResponse delete(Long ownerMemberId, Long tableId) {
        SharedTable sharedTable = getOwnedSharedTable(ownerMemberId, tableId);
        Instant now = clock.instant();

        sharedTable.softDelete(now);

        return new SharedTableIdResponse(sharedTable.getId());
    }

    private SharedTable getOwnedSharedTable(Long ownerMemberId, Long tableId) {
        SharedTable sharedTable = sharedTableRepository.findByIdAndDeletedAtIsNull(tableId)
                .orElseThrow(() -> new CustomException(SharedTableErrorCode.TABLE_ID_NOT_FOUND));

        restaurantOwnershipService.validateOwnedRestaurant(sharedTable.getRestaurantId(), ownerMemberId);

        return sharedTable;
    }

    private void validateCapacity(Integer capacity) {
        if (capacity == null || !ALLOWED_CAPACITIES.contains(capacity)) {
            throw new CustomException(SharedTableErrorCode.INVALID_TABLE_CAPACITY);
        }
    }
}
