package com.bobfull.sharedtable.adapter;

import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.sharedtable.port.SharedTableUsagePort;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 합석 테이블의 사용 여부 판단에 필요한 외부 도메인 조회를 한 곳에 둔다.
 */
@Component
public class SharedTableUsageAdapter implements SharedTableUsagePort {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED);

    private final TimeSlotRepository timeSlotRepository;
    private final ReservationRepository reservationRepository;

    public SharedTableUsageAdapter(
            TimeSlotRepository timeSlotRepository,
            ReservationRepository reservationRepository
    ) {
        this.timeSlotRepository = timeSlotRepository;
        this.reservationRepository = reservationRepository;
    }

    @Override
    public boolean hasDiningSession(Long tableId) {
        return timeSlotRepository.existsBySharedTableIdAndDeletedAtIsNull(tableId);
    }

    @Override
    public boolean hasActiveReservation(Long tableId) {
        List<Long> timeSlotIds = timeSlotRepository.findAllBySharedTableIdAndDeletedAtIsNull(tableId).stream()
                .map(TimeSlot::getId)
                .toList();
        return !timeSlotIds.isEmpty()
                && reservationRepository.existsByTimeSlotIdInAndReservationStatusIn(timeSlotIds, ACTIVE_STATUSES);
    }
}
