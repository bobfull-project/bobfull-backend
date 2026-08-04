package com.bobfull.reservation.repository;

import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.ReservationParticipant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationParticipantRepository extends JpaRepository<ReservationParticipant, Long>, MyReservationRepository {

    boolean existsByReservationIdAndMemberId(Long reservationId, Long memberId);

    @Query("select coalesce(sum(p.partySize), 0) from ReservationParticipant p "
            + "where p.reservationId = :reservationId and p.participationStatus = :status")
    int sumPartySize(@Param("reservationId") Long reservationId, @Param("status") ParticipationStatus status);

    Page<ReservationParticipant> findAllByReservationIdAndParticipationStatus(
            Long reservationId, ParticipationStatus status, Pageable pageable);

    Optional<ReservationParticipant> findByIdAndReservationId(Long id, Long reservationId);
}
