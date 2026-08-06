package com.bobfull.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bobfull.member.entity.Member;
import com.bobfull.member.repository.MemberRepository;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationParticipant;
import com.bobfull.reservation.port.ReservationNotificationPort;
import com.bobfull.reservation.port.ReservationNotificationPort.ReservationResultNotification;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.restaurant.entity.Restaurant;
import com.bobfull.restaurant.repository.RestaurantRepository;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationNotificationServiceTest {
    private static final Long RESERVATION_ID = 1L;
    private static final Long TIME_SLOT_ID = 10L;
    private static final Instant MEAL_START_AT = Instant.parse("2026-08-10T10:00:00Z");

    @Mock ReservationRepository reservationRepository;
    @Mock ReservationParticipantRepository reservationParticipantRepository;
    @Mock TimeSlotRepository timeSlotRepository;
    @Mock SharedTableRepository sharedTableRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock MemberRepository memberRepository;
    @Mock ReservationNotificationPort notificationPort;

    private ReservationNotificationService service() {
        return new ReservationNotificationService(
                reservationRepository, reservationParticipantRepository, timeSlotRepository,
                sharedTableRepository, restaurantRepository, memberRepository, notificationPort);
    }

    @Test
    void 확정_알림은_RESERVED_참여자에게만_보낸다() {
        ReservationParticipant participant = participant(2L, 20L);
        given_유효_참여자_조회(ParticipationStatus.RESERVED, List.of(participant));
        givenReservationChain();
        given(memberRepository.findAllById(List.of(20L))).willReturn(List.of(member(20L, "a@bobfull.com", "회원A")));

        service().notifyConfirmed(RESERVATION_ID);

        ArgumentCaptor<ReservationResultNotification> captor = ArgumentCaptor.forClass(ReservationResultNotification.class);
        verify(notificationPort).notifyConfirmed(captor.capture());
        ReservationResultNotification notification = captor.getValue();
        assertThat(notification.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(notification.restaurantName()).isEqualTo("밥풀식당");
        assertThat(notification.restaurantAddress()).isEqualTo("제주시");
        assertThat(notification.mealStartAt()).isEqualTo(MEAL_START_AT);
        assertThat(notification.recipients()).hasSize(1);
        assertThat(notification.recipients().get(0).email()).isEqualTo("a@bobfull.com");
    }

    @Test
    void 유효_참여자가_없으면_확정_알림을_보내지_않는다() {
        given_유효_참여자_조회(ParticipationStatus.RESERVED, List.of());

        service().notifyConfirmed(RESERVATION_ID);

        verify(notificationPort, never()).notifyConfirmed(any());
    }

    @Test
    void 취소_알림은_전달받은_참여자_ID로_조회해서_보낸다() {
        ReservationParticipant participant = participant(3L, 30L);
        given(reservationParticipantRepository.findAllById(List.of(3L))).willReturn(List.of(participant));
        givenReservationChain();
        given(memberRepository.findAllById(List.of(30L))).willReturn(List.of(member(30L, "b@bobfull.com", "회원B")));

        service().notifyCancelledDueToInsufficientParticipants(RESERVATION_ID, List.of(3L));

        ArgumentCaptor<ReservationResultNotification> captor = ArgumentCaptor.forClass(ReservationResultNotification.class);
        verify(notificationPort).notifyCancelledDueToInsufficientParticipants(captor.capture());
        assertThat(captor.getValue().recipients()).hasSize(1);
        assertThat(captor.getValue().recipients().get(0).email()).isEqualTo("b@bobfull.com");
    }

    @Test
    void 조회_중_예외가_발생해도_예약_결과에는_영향을_주지_않고_알림만_생략한다() {
        ReservationParticipant participant = participant(2L, 20L);
        given_유효_참여자_조회(ParticipationStatus.RESERVED, List.of(participant));
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.empty());

        assertThatCode(() -> service().notifyConfirmed(RESERVATION_ID)).doesNotThrowAnyException();

        verify(notificationPort, never()).notifyConfirmed(any());
    }

    private void given_유효_참여자_조회(ParticipationStatus status, List<ReservationParticipant> participants) {
        given(reservationParticipantRepository.findAllByReservationIdAndParticipationStatus(RESERVATION_ID, status))
                .willReturn(participants);
    }

    private void givenReservationChain() {
        Reservation reservation = Reservation.create(TIME_SLOT_ID, 99L);
        ReflectionTestUtils.setField(reservation, "id", RESERVATION_ID);
        TimeSlot timeSlot = TimeSlot.create(100L, MEAL_START_AT, MEAL_START_AT.plusSeconds(3600));
        ReflectionTestUtils.setField(timeSlot, "id", TIME_SLOT_ID);
        SharedTable sharedTable = SharedTable.create(1000L, 4);
        ReflectionTestUtils.setField(sharedTable, "id", 100L);
        Restaurant restaurant = Restaurant.create(9L, "밥풀식당", "제주시", "한식", "설명", "키워드", 10000);
        ReflectionTestUtils.setField(restaurant, "id", 1000L);

        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(reservation));
        given(timeSlotRepository.findByIdAndDeletedAtIsNull(TIME_SLOT_ID)).willReturn(Optional.of(timeSlot));
        given(sharedTableRepository.findByIdAndDeletedAtIsNull(100L)).willReturn(Optional.of(sharedTable));
        given(restaurantRepository.findByIdAndDeletedAtIsNull(1000L)).willReturn(Optional.of(restaurant));
    }

    private ReservationParticipant participant(Long id, Long memberId) {
        ReservationParticipant participant = ReservationParticipant.create(RESERVATION_ID, memberId, 1);
        ReflectionTestUtils.setField(participant, "id", id);
        return participant;
    }

    private Member member(Long id, String email, String name) {
        Member member = Member.createMember(email, "hash", name, "010-0000-0000");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
