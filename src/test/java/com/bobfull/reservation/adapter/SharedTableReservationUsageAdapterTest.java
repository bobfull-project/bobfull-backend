package com.bobfull.reservation.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.bobfull.reservation.repository.ReservationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SharedTableReservationUsageAdapterTest {

    @Mock private ReservationRepository reservationRepository;

    @Test
    void 연결된_회차의_활성_예약_여부를_예약_도메인_기준으로_조회한다() {
        // given
        given(reservationRepository.existsByTimeSlotIdInAndReservationStatusIn(any(), any())).willReturn(true);
        SharedTableReservationUsageAdapter adapter = new SharedTableReservationUsageAdapter(reservationRepository);

        // when
        boolean result = adapter.hasActiveReservation(List.of(200L));

        // then
        assertThat(result).isTrue();
    }

    @Test
    void 연결된_회차가_없으면_예약_조회_없이_사용되지_않음으로_판단한다() {
        // given
        SharedTableReservationUsageAdapter adapter = new SharedTableReservationUsageAdapter(reservationRepository);

        // when
        boolean result = adapter.hasActiveReservation(List.of());

        // then
        assertThat(result).isFalse();
    }
}
