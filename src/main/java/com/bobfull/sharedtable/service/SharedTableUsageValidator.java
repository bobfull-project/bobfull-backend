package com.bobfull.sharedtable.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.SharedTableErrorCode;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.repository.ReservationRepository;
import com.bobfull.timeslot.entity.TimeSlot;
import com.bobfull.timeslot.repository.TimeSlotRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 회차·예약 도메인이 연결될 때 합석 테이블 변경 가능 여부를 검증하는 경계다.
 */
@Service
public class SharedTableUsageValidator {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED);

    private final TimeSlotRepository timeSlotRepository;
    private final ReservationRepository reservationRepository;

    public SharedTableUsageValidator(TimeSlotRepository timeSlotRepository, ReservationRepository reservationRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.reservationRepository = reservationRepository;
    }

    public void validateCapacityChangeAllowed(Long tableId) {
        List<Long> timeSlotIds = timeSlotRepository.findAllBySharedTableIdAndDeletedAtIsNull(tableId).stream()
                .map(TimeSlot::getId)
                .toList();
        if (!timeSlotIds.isEmpty()
                && reservationRepository.existsByTimeSlotIdInAndReservationStatusIn(timeSlotIds, ACTIVE_STATUSES)) {
            throw new CustomException(SharedTableErrorCode.TABLE_HAS_RESERVATION);
        }
    }

    public void validateDeletionAllowed(Long tableId) {
        if (timeSlotRepository.existsBySharedTableIdAndDeletedAtIsNull(tableId)) {
            throw new CustomException(SharedTableErrorCode.TABLE_HAS_DINING_SESSION);
        }
    }
}
