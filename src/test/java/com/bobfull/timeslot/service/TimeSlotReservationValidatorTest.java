package com.bobfull.timeslot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.TimeSlotErrorCode;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.repository.ReservationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimeSlotReservationValidatorTest {

    @Mock
    private ReservationRepository reservationRepository;

    private TimeSlotReservationValidator validator() {
        return new TimeSlotReservationValidator(reservationRepository);
    }

    @Test
    void 활성_예약이_있는_회차는_수정할_수_없다() {
        // given
        given(reservationRepository.existsByTimeSlotIdAndReservationStatusIn(
                200L, List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED)))
                .willReturn(true);

        // when
        Throwable result = catchThrowable(() -> validator().validateChangeAllowed(200L));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(TimeSlotErrorCode.SESSION_HAS_RESERVATION);
    }

    @Test
    void 활성_예약이_없는_회차는_삭제할_수_있다() {
        // given
        given(reservationRepository.existsByTimeSlotIdAndReservationStatusIn(
                200L, List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED)))
                .willReturn(false);

        // when
        Throwable result = catchThrowable(() -> validator().validateDeletionAllowed(200L));

        // then
        assertThat(result).isNull();
    }
}
