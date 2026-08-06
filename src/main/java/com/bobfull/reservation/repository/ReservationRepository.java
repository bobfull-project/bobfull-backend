package com.bobfull.reservation.repository;

import com.bobfull.admin.repository.AdminReservationRepository;
import com.bobfull.reservation.entity.RecruitmentStatus;
import com.bobfull.reservation.entity.Reservation;
import com.bobfull.reservation.entity.ReservationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long>, ReservationSearchRepository, AdminReservationRepository,
        OwnerReservationRepository {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findWithLockById(Long reservationId);

    boolean existsByTimeSlotIdAndReservationStatusIn(Long timeSlotId, Collection<ReservationStatus> statuses);

    Optional<Reservation> findByTimeSlotIdAndReservationStatusIn(Long timeSlotId, Collection<ReservationStatus> statuses);

    boolean existsByTimeSlotIdInAndReservationStatusIn(Collection<Long> timeSlotIds, Collection<ReservationStatus> statuses);

    Page<Reservation> findAllByTimeSlotIdIn(Collection<Long> timeSlotIds, Pageable pageable);

    long countByReservationStatus(ReservationStatus status);

    @Query("select r from Reservation r join TimeSlot ts on r.timeSlotId = ts.id "
            + "join SharedTable st on ts.sharedTableId = st.id "
            + "where st.restaurantId = :restaurantId "
            + "and (:startAt is null or ts.startAt >= :startAt) and (:endAt is null or ts.startAt < :endAt)")
    Page<Reservation> findSettlementReservations(
            @Param("restaurantId") Long restaurantId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            Pageable pageable
    );

    /**
     * 모집 마감 기한(식사 시작 2시간 전) 도달 후보를 조회한다(Issue #47). TimeSlot과 조인해
     * 회차 시작 시각을 직접 비교하며, 아직 모집 마감·취소 처리되지 않은 대상만 후보로 삼는다.
     */
    @Query("select r.id from Reservation r join TimeSlot ts on r.timeSlotId = ts.id "
            + "where r.recruitmentStatus = :recruitmentStatus and r.reservationStatus in :activeStatuses "
            + "and ts.startAt <= :deadline "
            + "order by ts.startAt asc")
    List<Long> findRecruitmentDeadlineCandidateIds(
            @Param("recruitmentStatus") RecruitmentStatus recruitmentStatus,
            @Param("activeStatuses") Collection<ReservationStatus> activeStatuses,
            @Param("deadline") Instant deadline,
            Pageable pageable
    );
}
