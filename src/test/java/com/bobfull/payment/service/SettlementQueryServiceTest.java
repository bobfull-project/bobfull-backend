package com.bobfull.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import com.bobfull.common.exception.CommonErrorCode;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.RestaurantErrorCode;
import com.bobfull.payment.repository.PaymentRepository;
import com.bobfull.payment.repository.RefundRepository;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.repository.RestaurantRepository;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceTest {

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private SharedTableRepository sharedTableRepository;
    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;

    @Test
    void 타인식당의_정산은_403을_반환한다() {
        Restaurant restaurant = Restaurant.create(2L, "식당", "주소", "한식", "설명", "키워드", 10000);
        given(restaurantRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(restaurant));

        Throwable result = catchThrowable(() -> service().getExpectedSettlement(1L, 1L, null, null));

        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    @Test
    void 존재하지않는_식당의_정산은_404를_반환한다() {
        given(restaurantRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        Throwable result = catchThrowable(() -> service().getExpectedSettlement(1L, 1L, null, null));

        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(RestaurantErrorCode.RESTAURANT_ID_NOT_FOUND);
    }

    @Test
    void 역전된_기간은_400을_반환한다() {
        Restaurant restaurant = Restaurant.create(1L, "식당", "주소", "한식", "설명", "키워드", 10000);
        given(restaurantRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(restaurant));

        Throwable result = catchThrowable(() -> service().getExpectedSettlement(1L, 1L,
                LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1)));

        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void 타인식당의_예약상세정산은_403을_반환한다() {
        Reservation reservation = Reservation.create(2L, 10L);
        TimeSlot slot = TimeSlot.create(3L, Instant.parse("2026-08-01T09:00:00Z"), Instant.parse("2026-08-01T11:00:00Z"));
        SharedTable table = SharedTable.create(4L, 4);
        Restaurant restaurant = Restaurant.create(2L, "식당", "주소", "한식", "설명", "키워드", 10000);
        ReflectionTestUtils.setField(reservation, "id", 1L);
        ReflectionTestUtils.setField(slot, "id", 2L);
        ReflectionTestUtils.setField(table, "id", 3L);
        given(reservationRepository.findById(1L)).willReturn(Optional.of(reservation));
        given(timeSlotRepository.findById(2L)).willReturn(Optional.of(slot));
        given(sharedTableRepository.findById(3L)).willReturn(Optional.of(table));
        given(restaurantRepository.findByIdAndDeletedAtIsNull(4L)).willReturn(Optional.of(restaurant));

        Throwable result = catchThrowable(() -> service().getReservationSettlement(1L, 1L));

        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(CommonErrorCode.ACCESS_DENIED);
    }

    private SettlementQueryService service() {
        return new SettlementQueryService(restaurantRepository, sharedTableRepository, timeSlotRepository,
                reservationRepository, paymentRepository, refundRepository);
    }
}
