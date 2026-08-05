package com.bobfull.reservation.repository;

import com.bobfull.reservation.dto.OwnerReservationResult;
import com.bobfull.reservation.entity.ReservationStatus;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OwnerReservationRepository {

    Page<OwnerReservationResult> searchOwnerReservations(
            Long restaurantId,
            ReservationStatus reservationStatus,
            Instant startAt,
            Instant endAt,
            Instant now,
            Pageable pageable);
}
