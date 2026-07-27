package com.bobfull.reservation.repository;

import com.bobfull.reservation.entity.ParticipationStatus;
import com.bobfull.reservation.entity.ReservationParticipant;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationParticipantRepository extends JpaRepository<ReservationParticipant, Long> {

    boolean existsByReservationIdAndMemberIdAndParticipationStatus(
            Long reservationId, Long memberId, ParticipationStatus participationStatus);

    @Query("select coalesce(sum(p.partySize), 0) from ReservationParticipant p "
            + "where p.reservationId = :reservationId and p.participationStatus in :participationStatuses")
    int sumPartySizeByReservationIdAndParticipationStatusIn(
            @Param("reservationId") Long reservationId,
            @Param("participationStatuses") Collection<ParticipationStatus> participationStatuses);
}
