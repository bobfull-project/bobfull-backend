package com.bobfull.sharedtable.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.SharedTableErrorCode;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SharedTableUsageValidatorTest {

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private SharedTableUsageValidator validator() {
        return new SharedTableUsageValidator(timeSlotRepository, reservationRepository);
    }

    @Test
    void 활성_회차가_연결된_합석_테이블은_삭제할_수_없다() {
        // given
        given(timeSlotRepository.existsBySharedTableIdAndDeletedAtIsNull(100L)).willReturn(true);

        // when
        Throwable result = catchThrowable(() -> validator().validateDeletionAllowed(100L));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(SharedTableErrorCode.TABLE_HAS_DINING_SESSION);
    }

    @Test
    void 활성_예약이_연결된_회차가_있으면_정원을_변경할_수_없다() {
        // given
        TimeSlot timeSlot = timeSlot(200L);
        given(timeSlotRepository.findAllBySharedTableIdAndDeletedAtIsNull(100L)).willReturn(List.of(timeSlot));
        given(reservationRepository.existsByTimeSlotIdInAndReservationStatusIn(
                List.of(200L), List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED)))
                .willReturn(true);

        // when
        Throwable result = catchThrowable(() -> validator().validateCapacityChangeAllowed(100L));

        // then
        assertThat(result).isInstanceOf(CustomException.class);
        assertThat(((CustomException) result).getErrorCode()).isEqualTo(SharedTableErrorCode.TABLE_HAS_RESERVATION);
    }

    @Test
    void 연결된_회차가_없으면_정원을_변경할_수_있다() {
        // given
        given(timeSlotRepository.findAllBySharedTableIdAndDeletedAtIsNull(100L)).willReturn(List.of());

        // when
        Throwable result = catchThrowable(() -> validator().validateCapacityChangeAllowed(100L));

        // then
        assertThat(result).isNull();
    }

    private TimeSlot timeSlot(Long id) {
        TimeSlot timeSlot = TimeSlot.create(100L, java.time.Instant.parse("2026-08-01T02:00:00Z"),
                java.time.Instant.parse("2026-08-01T04:00:00Z"));
        ReflectionTestUtils.setField(timeSlot, "id", id);
        return timeSlot;
    }
}
