package com.bobfull.reservation.repository;

import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.ReservationParticipant;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /** §6-13 사장님용 참여자 목록 조회용이다. 상태 제한 없이 신청 이력 전체를 조회한다(Issue #147). */
    Page<ReservationParticipant> findAllByReservationId(Long reservationId, Pageable pageable);

    /**
     * CANCEL_REQUESTED인 참여자만 CANCELLED로 조건부 전환하는 원자적 UPDATE다(Issue #44 최종 계약).
     * 즉시 응답·웹훅·재확인 스케줄러가 같은 참여자를 동시에 완료 처리하려 해도, 이 조건절 덕분에
     * 오직 하나의 호출만 실제로 행을 갱신해 처리권을 얻는다 — 반환값이 1이면 이 호출이 처리권을
     * 얻은 것이고, 0이면 이미 다른 경로가 완료했거나 CANCEL_REQUESTED 상태가 아니므로 멱등 종료한다.
     */
    @Modifying
    @Query("update ReservationParticipant p set p.participationStatus = com.bobfull.reservation.entity.ParticipationStatus.CANCELLED, "
            + "p.cancelledAt = :cancelledAt "
            + "where p.id = :id and p.participationStatus = com.bobfull.reservation.entity.ParticipationStatus.CANCEL_REQUESTED")
    int completeCancelIfRequested(@Param("id") Long id, @Param("cancelledAt") Instant cancelledAt);

    Optional<ReservationParticipant> findByIdAndReservationId(Long id, Long reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReservationParticipant> findWithLockByIdAndReservationId(Long id, Long reservationId);

    long countByParticipationStatus(ParticipationStatus status);

    long countByParticipationStatusIn(Collection<ParticipationStatus> statuses);
}
