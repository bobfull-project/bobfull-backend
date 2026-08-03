package com.bobfull.reservation.adapter;

import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.sharedtable.port.SharedTableReservationUsagePort;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 활성 예약 상태의 정의를 예약 도메인에 유지한 채 합석 테이블 정책에 제공한다.
 */
@Component
public class SharedTableReservationUsageAdapter implements SharedTableReservationUsagePort {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED);

    private final ReservationRepository reservationRepository;

    public SharedTableReservationUsageAdapter(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Override
    public boolean hasActiveReservation(Collection<Long> timeSlotIds) {
        return !timeSlotIds.isEmpty()
                && reservationRepository.existsByTimeSlotIdInAndReservationStatusIn(timeSlotIds, ACTIVE_STATUSES);
    }
}
