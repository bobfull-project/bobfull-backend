package com.bobfull.timeslot.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.TimeSlotErrorCode;
import com.bobfull.reservation.entity.ReservationStatus;
import com.bobfull.reservation.repository.ReservationRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 예약 도메인이 연결될 때 회차 변경 가능 여부를 검증하는 경계다.
 */
@Service
public class TimeSlotReservationValidator {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
            List.of(ReservationStatus.RECRUITING, ReservationStatus.CONFIRMED);

    private final ReservationRepository reservationRepository;

    public TimeSlotReservationValidator(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    public void validateChangeAllowed(Long sessionId) {
        if (reservationRepository.existsByTimeSlotIdAndReservationStatusIn(sessionId, ACTIVE_STATUSES)) {
            throw new CustomException(TimeSlotErrorCode.SESSION_HAS_RESERVATION);
        }
    }

    public void validateDeletionAllowed(Long sessionId) {
        validateChangeAllowed(sessionId);
    }
}
