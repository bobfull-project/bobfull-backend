package com.bobfull.reservation.repository;

import com.bobfull.reservation.dto.MyReservationResult;
import com.bobfull.reservation.entity.ReservationStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MyReservationRepository {

    Page<MyReservationResult> searchMyReservations(Long memberId, ReservationStatus reservationStatus, Pageable pageable);

    Optional<MyReservationResult> findMyReservationDetail(Long memberId, Long reservationId);
}
