package com.bobfull.reservation.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.bobfull.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimeSlotReservationUsageAdapterTest {

    @Mock private ReservationRepository reservationRepository;

    @Test
    void 활성_예약_여부는_예약_도메인의_상태_정의로_조회한다() {
        // given
        given(reservationRepository.existsByTimeSlotIdAndReservationStatusIn(any(), any())).willReturn(true);
        TimeSlotReservationUsageAdapter adapter = new TimeSlotReservationUsageAdapter(reservationRepository);

        // when
        boolean result = adapter.hasActiveReservation(200L);

        // then
        assertThat(result).isTrue();
        verify(reservationRepository).existsByTimeSlotIdAndReservationStatusIn(any(), any());
    }
}
