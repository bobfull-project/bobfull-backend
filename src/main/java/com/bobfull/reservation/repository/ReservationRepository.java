package com.bobfull.reservation.repository;

import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByTimeSlotIdAndReservationStatusIn(Long timeSlotId, Collection<ReservationStatus> reservationStatuses);
}
