package com.bobfull.performance;

import static org.assertj.core.api.Assertions.assertThat;
import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.payment.repository.PaymentRepository;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.repository.RestaurantRepository;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import com.bobfull.timeslot.service.TimeSlotService;
import com.bobfull.payment.service.SettlementQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** performance 프로필의 Docker MySQL에서 Controller 요청별 JDBC prepare 수를 기록한다. */
@SpringBootTest
@ActiveProfiles("performance")
@EnabledIfEnvironmentVariable(named = "BOBFULL_PERF_DB_URL", matches = ".+")
class PerformanceQueryCountIntegrationTest {

    @Autowired private TimeSlotService timeSlotService;
    @Autowired private SettlementQueryService settlementQueryService;
    @Autowired private jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private SharedTableRepository sharedTableRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PaymentRepository paymentRepository;

    private Long restaurantId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        sharedTableRepository.deleteAll();
        restaurantRepository.deleteAll();
        Restaurant restaurant = restaurantRepository.save(Restaurant.create(1L, "측정 식당", "제주", "한식", "설명", "측정", 10000));
        restaurantId = restaurant.getId();
        SharedTable table = sharedTableRepository.save(SharedTable.create(restaurantId, 4));
        List<TimeSlot> slots = timeSlotRepository.saveAll(List.of(
                TimeSlot.create(table.getId(), Instant.parse("2026-08-10T08:00:00Z"), Instant.parse("2026-08-10T10:00:00Z")),
                TimeSlot.create(table.getId(), Instant.parse("2026-08-10T10:00:00Z"), Instant.parse("2026-08-10T12:00:00Z"))));
        Reservation reservation = reservationRepository.save(Reservation.create(slots.get(0).getId(), 1L));
        Payment payment = Payment.createReady("perf-query-count", 1L, slots.get(0).getId(), reservation.getId(), PaymentPurpose.JOIN, 1, BigDecimal.valueOf(10000), Instant.parse("2026-08-09T00:00:00Z"));
        payment.complete(Instant.parse("2026-08-09T00:01:00Z"));
        paymentRepository.save(payment);
    }

    @Test
    void 예약가능회차_조회_쿼리수를_기록한다() throws Exception {
        Statistics statistics = statistics();
        statistics.clear();
        assertThat(timeSlotService.getAvailableDiningSessions(restaurantId, java.time.LocalDate.parse("2026-08-10"), null).content()).hasSize(2);
        assertThat(statistics.getPrepareStatementCount()).isPositive();
        System.out.println("PERF_QUERY_COUNT available-dining-sessions=" + statistics.getPrepareStatementCount());
    }

    @Test
    void 지급예정정산총액_조회_쿼리수를_기록한다() throws Exception {
        Statistics statistics = statistics();
        statistics.clear();
        assertThat(settlementQueryService.getExpectedSettlement(1L, restaurantId, java.time.LocalDate.parse("2026-08-10"), java.time.LocalDate.parse("2026-08-10")).expectedSettlementAmount()).isEqualByComparingTo("10000");
        assertThat(statistics.getPrepareStatementCount()).isPositive();
        System.out.println("PERF_QUERY_COUNT expected-settlement=" + statistics.getPrepareStatementCount());
    }

    @Test
    void 예약별지급예정목록_조회_쿼리수를_기록한다() throws Exception {
        Statistics statistics = statistics();
        statistics.clear();
        assertThat(settlementQueryService.getReservationSettlements(1L, restaurantId, java.time.LocalDate.parse("2026-08-10"), java.time.LocalDate.parse("2026-08-10"), org.springframework.data.domain.PageRequest.of(0, 20)).content()).hasSize(1);
        assertThat(statistics.getPrepareStatementCount()).isPositive();
        System.out.println("PERF_QUERY_COUNT settlement-list=" + statistics.getPrepareStatementCount());
    }

    private Statistics statistics() { return entityManagerFactory.unwrap(SessionFactory.class).getStatistics(); }
}
