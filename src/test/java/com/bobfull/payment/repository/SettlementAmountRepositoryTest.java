package com.bobfull.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.payment.entity.Refund;
import com.bobfull.payment.entity.RefundStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.repository.RestaurantRepository;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class SettlementAmountRepositoryTest {

    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private SharedTableRepository sharedTableRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;

    @Test
    void 결제완료이력에서는_완료환불만_한번차감한다() {
        // given
        Restaurant restaurant = restaurantRepository.save(Restaurant.create(1L, "식당", "주소", "한식", "설명", "키워드", 10000));
        SharedTable table = sharedTableRepository.save(SharedTable.create(restaurant.getId(), 4));
        TimeSlot slot = timeSlotRepository.save(TimeSlot.create(table.getId(),
                Instant.parse("2026-08-01T09:00:00Z"), Instant.parse("2026-08-01T11:00:00Z")));
        Reservation reservation = reservationRepository.save(Reservation.create(slot.getId(), 2L));
        Payment paid = Payment.createReady("paid", 2L, slot.getId(), reservation.getId(), PaymentPurpose.JOIN, 1,
                BigDecimal.valueOf(30000), Instant.parse("2026-08-01T08:00:00Z"));
        paid.complete(Instant.parse("2026-08-01T08:01:00Z"));
        paymentRepository.save(paid);
        refundRepository.save(Refund.create(paid, BigDecimal.valueOf(10000), RefundStatus.COMPLETED,
                Instant.parse("2026-08-01T08:02:00Z"), Instant.parse("2026-08-01T08:03:00Z")));

        // when
        Object[] amounts = paymentRepository.sumSettlementAmounts(restaurant.getId(), RefundStatus.COMPLETED,
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z")).get(0);

        // then
        assertThat((BigDecimal) amounts[0]).isEqualByComparingTo("30000");
        assertThat((BigDecimal) amounts[1]).isEqualByComparingTo("10000");
    }
}
