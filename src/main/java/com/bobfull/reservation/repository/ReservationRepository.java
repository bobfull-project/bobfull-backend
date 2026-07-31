package com.bobfull.reservation.repository;

import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationStatus;
import java.util.Collection;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, ReservationSearchRepository {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findWithLockById(Long reservationId);

    boolean existsByTimeSlotIdAndReservationStatusIn(Long timeSlotId, Collection<ReservationStatus> statuses);

    Optional<Reservation> findByTimeSlotIdAndReservationStatusIn(Long timeSlotId, Collection<ReservationStatus> statuses);

    boolean existsByTimeSlotIdInAndReservationStatusIn(Collection<Long> timeSlotIds, Collection<ReservationStatus> statuses);

    Page<Reservation> findAllByTimeSlotIdIn(Collection<Long> timeSlotIds, Pageable pageable);

    @Query("select r from Reservation r join TimeSlot ts on r.timeSlotId = ts.id "
            + "join SharedTable st on ts.sharedTableId = st.id "
            + "where st.restaurantId = :restaurantId and ts.deletedAt is null "
            + "and (:startAt is null or ts.startAt >= :startAt) and (:endAt is null or ts.startAt < :endAt)")
    Page<Reservation> findSettlementReservations(
            @Param("restaurantId") Long restaurantId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            Pageable pageable
    );
}
