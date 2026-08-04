package com.bobfull.reservation.repository;

import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.ReservationParticipant;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationParticipantRepository extends JpaRepository<ReservationParticipant, Long>, MyReservationRepository {

    boolean existsByReservationIdAndMemberId(Long reservationId, Long memberId);

    boolean existsByReservationId(Long reservationId);

    boolean existsByReservationIdAndParticipationStatus(Long reservationId, ParticipationStatus status);

    Optional<ReservationParticipant> findByReservationIdAndMemberId(Long reservationId, Long memberId);

    List<ReservationParticipant> findAllByReservationIdAndParticipationStatus(
            Long reservationId, ParticipationStatus participationStatus);

    @Query("select coalesce(sum(p.partySize), 0) from ReservationParticipant p "
            + "where p.reservationId = :reservationId and p.participationStatus = :status")
    int sumPartySize(@Param("reservationId") Long reservationId, @Param("status") ParticipationStatus status);

    /**
     * 여러 참여 상태에 걸친 partySize 합계다. 취소 접수(CANCEL_REQUESTED) 참여자는 환불이 완료되기
     * 전까지 좌석을 계속 점유한 상태로 집계해야 하므로(Issue #44), RESERVED와 함께 넘겨 합산한다.
     */
    @Query("select coalesce(sum(p.partySize), 0) from ReservationParticipant p "
            + "where p.reservationId = :reservationId and p.participationStatus in :statuses")
    int sumPartySizeByStatuses(
            @Param("reservationId") Long reservationId, @Param("statuses") Collection<ParticipationStatus> statuses);

    Page<ReservationParticipant> findAllByReservationIdAndParticipationStatus(
            Long reservationId, ParticipationStatus status, Pageable pageable);

    Optional<ReservationParticipant> findByIdAndReservationId(Long id, Long reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReservationParticipant> findWithLockByIdAndReservationId(Long id, Long reservationId);

    long countByParticipationStatus(ParticipationStatus status);

    long countByParticipationStatusIn(Collection<ParticipationStatus> statuses);
}
