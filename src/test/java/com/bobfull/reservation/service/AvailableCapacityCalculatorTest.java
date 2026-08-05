package com.bobfull.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bobfull.payment.service.PaymentHoldReader;
import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.repository.ReservationParticipantRepository;
import com.bobfull.reservation.repository.ReservationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AvailableCapacityCalculatorTest {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLING);
    private static final List<ParticipationStatus> OCCUPYING_STATUSES =
            List.of(ParticipationStatus.RESERVED, ParticipationStatus.CANCEL_REQUESTED);

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationParticipantRepository reservationParticipantRepository;

    @Mock
    private PaymentHoldReader paymentHoldReader;

    private AvailableCapacityCalculator calculator() {
        return new AvailableCapacityCalculator(reservationRepository, reservationParticipantRepository, paymentHoldReader);
    }

    @Test
    void 활성_예약이_없으면_임시_선점만_차감한다() {
        // given
        given(reservationRepository.findByTimeSlotIdAndReservationStatusIn(200L, ACTIVE_STATUSES))
                .willReturn(Optional.empty());
        given(paymentHoldReader.sumActiveReadyPartySize(200L)).willReturn(1);

        // when
        int availableCapacity = calculator().calculate(200L, 4);

        // then
        assertThat(availableCapacity).isEqualTo(3);
    }

    @Test
    void 활성_예약이_있으면_참여_인원과_임시_선점을_함께_차감한다() {
        // given
        Reservation reservation = reservation(10L);
        given(reservationRepository.findByTimeSlotIdAndReservationStatusIn(200L, ACTIVE_STATUSES))
                .willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.sumPartySizeByStatuses(10L, OCCUPYING_STATUSES)).willReturn(2);
        given(paymentHoldReader.sumActiveReadyPartySize(200L)).willReturn(1);

        // when
        int availableCapacity = calculator().calculate(200L, 4);

        // then
        assertThat(availableCapacity).isEqualTo(1);
    }

    @Test
    void 정원을_초과해_차감되어도_음수를_반환하지_않는다() {
        // given
        Reservation reservation = reservation(10L);
        given(reservationRepository.findByTimeSlotIdAndReservationStatusIn(200L, ACTIVE_STATUSES))
                .willReturn(Optional.of(reservation));
        given(reservationParticipantRepository.sumPartySizeByStatuses(10L, OCCUPYING_STATUSES)).willReturn(4);
        given(paymentHoldReader.sumActiveReadyPartySize(200L)).willReturn(2);

        // when
        int availableCapacity = calculator().calculate(200L, 4);

        // then
        assertThat(availableCapacity).isZero();
    }

    private Reservation reservation(Long id) {
        Reservation reservation = Reservation.create(200L, 1L);
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }
}
