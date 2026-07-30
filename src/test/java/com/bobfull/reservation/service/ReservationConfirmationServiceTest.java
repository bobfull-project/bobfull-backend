package com.bobfull.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.RecruitmentStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationParticipant;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.sharedtable.entity.SharedTable;
import com.bobfull.sharedtable.repository.SharedTableRepository;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationConfirmationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationParticipantRepository reservationParticipantRepository;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private SharedTableRepository sharedTableRepository;

    private ReservationConfirmationService service() {
        return new ReservationConfirmationService(
                reservationRepository, reservationParticipantRepository, timeSlotRepository, sharedTableRepository);
    }

    @Test
    void CREATE는_새_예약과_최초_참여자를_생성하고_확정_기준_미달이면_RECRUITING_OPEN을_유지한다() {
        // given
        TimeSlot timeSlot = timeSlot(200L, 100L);
        SharedTable sharedTable = sharedTable(100L, 4);
        given(reservationRepository.save(any(Reservation.class))).willAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            ReflectionTestUtils.setField(reservation, "id", 10L);
            return reservation;
        });
        given(reservationParticipantRepository.save(any(ReservationParticipant.class))).willAnswer(invocation -> {
            ReservationParticipant participant = invocation.getArgument(0);
            ReflectionTestUtils.setField(participant, "id", 20L);
            return participant;
        });
        given(timeSlotRepository.findByIdAndDeletedAtIsNull(200L)).willReturn(Optional.of(timeSlot));
        given(sharedTableRepository.findByIdAndDeletedAtIsNull(100L)).willReturn(Optional.of(sharedTable));
        given(reservationParticipantRepository.sumPartySize(10L, ParticipationStatus.RESERVED)).willReturn(2);

        // when
        ReservationConfirmationService.ReservationConfirmationResult result =
                service().confirm(PaymentPurpose.CREATE, 200L, null, 1L, 2);

        // then
        assertThat(result.reservationId()).isEqualTo(10L);
        assertThat(result.reservationParticipantId()).isEqualTo(20L);
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void JOIN으로_정원_4명중_3명_확정_기준에_도달하면_CONFIRMED_OPEN을_유지한다() {
        // given
        Reservation reservation = reservation(10L);
        TimeSlot timeSlot = timeSlot(200L, 100L);
        SharedTable sharedTable = sharedTable(100L, 4);
        given(reservationRepository.findById(10L)).willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.save(any(ReservationParticipant.class))).willAnswer(invocation -> {
            ReservationParticipant participant = invocation.getArgument(0);
            ReflectionTestUtils.setField(participant, "id", 21L);
            return participant;
        });
        given(timeSlotRepository.findByIdAndDeletedAtIsNull(200L)).willReturn(Optional.of(timeSlot));
        given(sharedTableRepository.findByIdAndDeletedAtIsNull(100L)).willReturn(Optional.of(sharedTable));
        given(reservationParticipantRepository.sumPartySize(10L, ParticipationStatus.RESERVED)).willReturn(3);

        // when
        service().confirm(PaymentPurpose.JOIN, 200L, 10L, 2L, 1);

        // then
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.OPEN);
    }

    @Test
    void JOIN으로_정원에_완전히_도달하면_CONFIRMED_CLOSED로_전이한다() {
        // given
        Reservation reservation = reservation(10L);
        TimeSlot timeSlot = timeSlot(200L, 100L);
        SharedTable sharedTable = sharedTable(100L, 4);
        given(reservationRepository.findById(10L)).willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.save(any(ReservationParticipant.class))).willAnswer(invocation -> {
            ReservationParticipant participant = invocation.getArgument(0);
            ReflectionTestUtils.setField(participant, "id", 21L);
            return participant;
        });
        given(timeSlotRepository.findByIdAndDeletedAtIsNull(200L)).willReturn(Optional.of(timeSlot));
        given(sharedTableRepository.findByIdAndDeletedAtIsNull(100L)).willReturn(Optional.of(sharedTable));
        given(reservationParticipantRepository.sumPartySize(10L, ParticipationStatus.RESERVED)).willReturn(4);

        // when
        ReservationConfirmationService.ReservationConfirmationResult result =
                service().confirm(PaymentPurpose.JOIN, 200L, 10L, 2L, 1);

        // then
        assertThat(result.reservationId()).isEqualTo(10L);
        assertThat(result.reservationParticipantId()).isEqualTo(21L);
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.CLOSED);
    }

    @Test
    void 정원_2명_테이블은_확정_기준도_정원과_같아_2명이_차면_CONFIRMED_CLOSED로_전이한다() {
        // given
        Reservation reservation = reservation(10L);
        TimeSlot timeSlot = timeSlot(200L, 100L);
        SharedTable sharedTable = sharedTable(100L, 2);
        given(reservationRepository.findById(10L)).willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.save(any(ReservationParticipant.class))).willAnswer(invocation -> {
            ReservationParticipant participant = invocation.getArgument(0);
            ReflectionTestUtils.setField(participant, "id", 22L);
            return participant;
        });
        given(timeSlotRepository.findByIdAndDeletedAtIsNull(200L)).willReturn(Optional.of(timeSlot));
        given(sharedTableRepository.findByIdAndDeletedAtIsNull(100L)).willReturn(Optional.of(sharedTable));
        given(reservationParticipantRepository.sumPartySize(10L, ParticipationStatus.RESERVED)).willReturn(2);

        // when
        service().confirm(PaymentPurpose.JOIN, 200L, 10L, 2L, 1);

        // then
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getRecruitmentStatus()).isEqualTo(RecruitmentStatus.CLOSED);
    }

    private Reservation reservation(Long id) {
        Reservation reservation = Reservation.create(200L, 1L);
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

    private TimeSlot timeSlot(Long id, Long sharedTableId) {
        TimeSlot timeSlot = TimeSlot.create(sharedTableId, Instant.parse("2026-08-01T02:00:00Z"),
                Instant.parse("2026-08-01T04:00:00Z"));
        ReflectionTestUtils.setField(timeSlot, "id", id);
        return timeSlot;
    }

    private SharedTable sharedTable(Long id, Integer capacity) {
        SharedTable sharedTable = SharedTable.create(1L, capacity);
        ReflectionTestUtils.setField(sharedTable, "id", id);
        return sharedTable;
    }
}
