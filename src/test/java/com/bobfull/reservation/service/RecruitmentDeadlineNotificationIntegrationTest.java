package com.bobfull.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bobfull.member.entity.Member;
import com.bobfull.member.repository.MemberRepository;
import com.bobfull.notification.adapter.FakeReservationNotificationAdapter;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationParticipant;
import com.bobfull.reservation.port.ReservationNotificationPort;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.repository.RestaurantRepository;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모집 마감 처리(#47)의 핵심 트랜잭션 커밋·롤백에 따라 이메일 알림 이벤트가 실제로 처리되거나
 * 처리되지 않는지 검증한다(Issue #168 V2). {@code @Async} 리스너를 테스트에서 결정적으로
 * 검증하기 위해 {@code emailTaskExecutor}를 동기 실행 Executor로 교체한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recruitment-deadline-notification-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=recruitment-deadline-notification-test-secret-key-please-keep-long",
        "jwt.access-token-expiration-seconds=1800",
        "portone.api-secret=portone-recruitment-deadline-test-api-secret",
        "portone.store-id=portone-recruitment-deadline-test-store-id",
        "portone.webhook-secret=d2hzZWNfcmVjcnVpdG1lbnQtdGVzdA==",
        // emailTaskExecutor를 테스트 전용 동기 실행 Executor로 완전히 교체하기 위해 필요하다.
        "spring.main.allow-bean-definition-overriding=true"
})
@ContextConfiguration(classes = RecruitmentDeadlineNotificationIntegrationTest.TestConfig.class)
class RecruitmentDeadlineNotificationIntegrationTest {

    @Autowired private ReservationCancellationTransactionService transactionService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationParticipantRepository reservationParticipantRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private SharedTableRepository sharedTableRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private FakeReservationNotificationAdapter notificationAdapter;
    @Autowired private RollbackForcingService rollbackForcingService;

    @AfterEach
    void cleanUp() {
        notificationAdapter.confirmedNotifications().clear();
        notificationAdapter.cancelledNotifications().clear();
        reservationParticipantRepository.deleteAll();
        reservationRepository.deleteAll();
        timeSlotRepository.deleteAll();
        sharedTableRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void 핵심_트랜잭션이_커밋되면_확정_알림_이벤트가_처리된다() {
        Reservation reservation = givenConfirmableReservation();

        transactionService.acceptRecruitmentDeadline(reservation.getId());

        assertThat(notificationAdapter.confirmedNotifications()).hasSize(3);
        assertThat(notificationAdapter.confirmedNotifications().get(0).reservationId()).isEqualTo(reservation.getId());
        assertThat(notificationAdapter.cancelledNotifications()).isEmpty();
    }

    @Test
    void 핵심_트랜잭션이_커밋되면_취소_알림_이벤트가_처리된다() {
        Reservation reservation = givenUnderThresholdReservation();

        transactionService.acceptRecruitmentDeadline(reservation.getId());

        assertThat(notificationAdapter.cancelledNotifications()).hasSize(2);
        assertThat(notificationAdapter.confirmedNotifications()).isEmpty();
    }

    @Test
    void 핵심_트랜잭션이_롤백되면_알림_이벤트가_처리되지_않는다() {
        Reservation reservation = givenConfirmableReservation();

        assertThatThrownBy(() -> rollbackForcingService.acceptThenFail(reservation.getId()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(notificationAdapter.confirmedNotifications()).isEmpty();
        assertThat(notificationAdapter.cancelledNotifications()).isEmpty();
        // 트랜잭션 자체가 롤백됐으므로 모집 마감 처리도 반영되지 않는다.
        Reservation reloaded = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(reloaded.getRecruitmentStatus().name()).isEqualTo("OPEN");
    }

    private Reservation givenConfirmableReservation() {
        // capacity 4, threshold 3, 참여자 3명 → 확정 기준 충족
        return reservationWithParticipants(4, 3);
    }

    private Reservation givenUnderThresholdReservation() {
        // capacity 4, threshold 3, 참여자 2명 → 확정 기준 미달
        return reservationWithParticipants(4, 2);
    }

    private Reservation reservationWithParticipants(int capacity, int participantCount) {
        Member owner = memberRepository.saveAndFlush(Member.createOwner(
                "owner-" + System.nanoTime() + "@bobfull.com", "hash", "사장님", "010-1111-1111", "000-00-00000"));
        Restaurant restaurant = restaurantRepository.saveAndFlush(
                Restaurant.create(owner.getId(), "밥풀식당", "제주시", "한식", "설명", "키워드", 10000));
        SharedTable sharedTable = sharedTableRepository.saveAndFlush(SharedTable.create(restaurant.getId(), capacity));
        TimeSlot timeSlot = timeSlotRepository.saveAndFlush(TimeSlot.create(
                sharedTable.getId(), Instant.parse("2026-09-01T02:00:00Z"), Instant.parse("2026-09-01T04:00:00Z")));

        Member creator = memberRepository.saveAndFlush(Member.createMember(
                "creator-" + System.nanoTime() + "@bobfull.com", "hash", "회원", "010-2222-2222"));
        Reservation reservation = reservationRepository.saveAndFlush(Reservation.create(timeSlot.getId(), creator.getId()));
        for (int i = 0; i < participantCount; i++) {
            Member participantMember = memberRepository.saveAndFlush(Member.createMember(
                    "participant-" + System.nanoTime() + "-" + i + "@bobfull.com", "hash", "참여자" + i, "010-333%d-3333".formatted(i)));
            reservationParticipantRepository.saveAndFlush(
                    ReservationParticipant.create(reservation.getId(), participantMember.getId(), 1));
        }
        return reservation;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean
        @Primary
        ReservationNotificationPort fakeReservationNotificationPort() {
            return new FakeReservationNotificationAdapter();
        }

        @Bean(com.bobfull.notification.config.NotificationAsyncConfig.EMAIL_TASK_EXECUTOR)
        @Primary
        TaskExecutor synchronousEmailTaskExecutor() {
            // AFTER_COMMIT + @Async 리스너를 테스트에서 결정적으로 검증하기 위해 동기 실행으로 대체한다.
            return new SyncTaskExecutor();
        }

        @Bean
        RollbackForcingService rollbackForcingService(ReservationCancellationTransactionService transactionService) {
            return new RollbackForcingService(transactionService);
        }
    }

    /** 모집 마감 처리 후 강제로 예외를 던져 같은 트랜잭션 전체를 롤백시키는 테스트 전용 서비스다. */
    static class RollbackForcingService {
        private final ReservationCancellationTransactionService transactionService;

        RollbackForcingService(ReservationCancellationTransactionService transactionService) {
            this.transactionService = transactionService;
        }

        @Transactional
        public void acceptThenFail(Long reservationId) {
            transactionService.acceptRecruitmentDeadline(reservationId);
            throw new IllegalStateException("강제 롤백(테스트)");
        }
    }
}
