package com.bobfull.reservation.adapter;

import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.timeslot.port.TimeSlotReservationUsagePort;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 활성 예약 상태의 정의를 예약 도메인에 유지한 채 회차 변경 정책에 제공한다.
 */
@Component
public class TimeSlotReservationUsageAdapter implements TimeSlotReservationUsagePort {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED, ReservationStatus.CANCELLING);

    private final ReservationRepository reservationRepository;

    public TimeSlotReservationUsageAdapter(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Override
    public boolean hasActiveReservation(Long timeSlotId) {
        return reservationRepository.existsByTimeSlotIdAndReservationStatusIn(timeSlotId, ACTIVE_STATUSES);
    }
}
