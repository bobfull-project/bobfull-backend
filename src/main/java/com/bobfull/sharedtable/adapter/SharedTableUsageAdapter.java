package com.bobfull.sharedtable.adapter;

import com.bobfull.sharedtable.port.SharedTableReservationUsagePort;
import com.bobfull.sharedtable.port.SharedTableUsagePort;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import org.springframework.stereotype.Component;

/**
 * 합석 테이블의 사용 여부 판단에 필요한 외부 도메인 조회를 한 곳에 둔다.
 */
@Component
public class SharedTableUsageAdapter implements SharedTableUsagePort {

    private final TimeSlotRepository timeSlotRepository;
    private final SharedTableReservationUsagePort reservationUsagePort;

    public SharedTableUsageAdapter(
            TimeSlotRepository timeSlotRepository,
            SharedTableReservationUsagePort reservationUsagePort
    ) {
        this.timeSlotRepository = timeSlotRepository;
        this.reservationUsagePort = reservationUsagePort;
    }

    @Override
    public boolean hasDiningSession(Long tableId) {
        return timeSlotRepository.existsBySharedTableIdAndDeletedAtIsNull(tableId);
    }

    @Override
    public boolean hasActiveReservation(Long tableId) {
        return reservationUsagePort.hasActiveReservation(
                timeSlotRepository.findAllBySharedTableIdAndDeletedAtIsNull(tableId).stream()
                .map(TimeSlot::getId)
                .toList());
    }
}
