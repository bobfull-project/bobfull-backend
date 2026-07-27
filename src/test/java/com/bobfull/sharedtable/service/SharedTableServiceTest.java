package com.bobfull.sharedtable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bobfull.common.exception.CommonErrorCode;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SharedTableServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-27T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private SharedTableRepository sharedTableRepository;

    @Mock
    private TemporaryRestaurantOwnershipService restaurantOwnershipService;

    private SharedTableService sharedTableService;

    @BeforeEach
    void setUp() {
        sharedTableService = new SharedTableService(
                sharedTableRepository,
                restaurantOwnershipService,
                FIXED_CLOCK
        );
    }

    @Test
    void 허용된_정원이면_본인_식당에_합석_테이블을_등록한다() {
        // given
        when(sharedTableRepository.save(any(SharedTable.class)))
                .thenAnswer(invocation -> {
                    SharedTable sharedTable = invocation.getArgument(0);
                    ReflectionTestUtils.setField(sharedTable, "id", 100L);
                    return sharedTable;
                });

        // when
        SharedTableIdResponse result = sharedTableService.create(
                1L,
                10L,
                new SharedTableCreateRequest(4)
        );

        // then
        assertThat(result.tableId()).isEqualTo(100L);
    }

    @Test
    void 허용되지_않은_정원이면_식당_조회_전에_INVALID_TABLE_CAPACITY를_반환한다() {
        // when
        Throwable result = catchThrowable(
                () -> sharedTableService.create(1L, 10L, new SharedTableCreateRequest(3)));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(SharedTableErrorCode.INVALID_TABLE_CAPACITY);
        verify(restaurantOwnershipService, never()).validateOwnedRestaurant(any(), any());
    }

    @Test
    void 본인_식당의_삭제되지_않은_테이블_목록을_페이지로_반환한다() {
        // given
        SharedTable sharedTable = sharedTable(100L, 10L, 4);
        PageRequest pageable = PageRequest.of(0, 20);

        when(sharedTableRepository.findAllByRestaurantIdAndDeletedAtIsNull(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(sharedTable), pageable, 1));

        // when
        SharedTableListResponse result = sharedTableService.getTables(1L, 10L, pageable);

        // then
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).tableId()).isEqualTo(100L);
        assertThat(result.content().get(0).restaurantId()).isEqualTo(10L);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void 본인_테이블이면_상세를_반환한다() {
        // given
        SharedTable sharedTable = sharedTable(100L, 10L, 8);
        when(sharedTableRepository.findByIdAndDeletedAtIsNull(100L))
                .thenReturn(Optional.of(sharedTable));

        // when
        SharedTableResponse result = sharedTableService.getTable(1L, 100L);

        // then
        assertThat(result.tableId()).isEqualTo(100L);
        assertThat(result.restaurantId()).isEqualTo(10L);
        assertThat(result.capacity()).isEqualTo(8);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void 타인_식당의_테이블이면_ACCESS_DENIED를_반환한다() {
        // given
        SharedTable sharedTable = sharedTable(100L, 10L, 4);
        when(sharedTableRepository.findByIdAndDeletedAtIsNull(100L))
                .thenReturn(Optional.of(sharedTable));
        doThrow(new CustomException(CommonErrorCode.ACCESS_DENIED))
                .when(restaurantOwnershipService)
                .validateOwnedRestaurant(10L, 1L);

        // when
        Throwable result = catchThrowable(() -> sharedTableService.getTable(1L, 100L));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    void 테이블이_없으면_TABLE_ID_NOT_FOUND를_반환한다() {
        // given
        when(sharedTableRepository.findByIdAndDeletedAtIsNull(100L))
                .thenReturn(Optional.empty());

        // when
        Throwable result = catchThrowable(() -> sharedTableService.getTable(1L, 100L));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(SharedTableErrorCode.TABLE_ID_NOT_FOUND);
    }

    @Test
    void 수정시_허용된_정원이면_테이블_정원을_변경한다() {
        // given
        SharedTable sharedTable = sharedTable(100L, 10L, 2);
        when(sharedTableRepository.findByIdAndDeletedAtIsNull(100L))
                .thenReturn(Optional.of(sharedTable));

        // when
        SharedTableIdResponse result = sharedTableService.update(
                1L,
                100L,
                new SharedTableUpdateRequest(8)
        );

        // then
        assertThat(result.tableId()).isEqualTo(100L);
        assertThat(sharedTable.getCapacity()).isEqualTo(8);
    }

    @Test
    void 삭제시_테이블에_deletedAt을_기록한다() {
        // given
        SharedTable sharedTable = sharedTable(100L, 10L, 4);
        when(sharedTableRepository.findByIdAndDeletedAtIsNull(100L))
                .thenReturn(Optional.of(sharedTable));

        // when
        SharedTableIdResponse result = sharedTableService.delete(1L, 100L);

        // then
        assertThat(result.tableId()).isEqualTo(100L);
        assertThat(sharedTable.getDeletedAt()).isEqualTo(FIXED_CLOCK.instant());
    }

    private SharedTable sharedTable(Long tableId, Long restaurantId, int capacity) {
        SharedTable sharedTable = SharedTable.create(restaurantId, capacity);
        ReflectionTestUtils.setField(sharedTable, "id", tableId);
        return sharedTable;
    }
}
