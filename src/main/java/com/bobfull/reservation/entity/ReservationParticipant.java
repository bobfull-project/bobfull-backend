package com.bobfull.reservation.entity;

import com.bobfull.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 예약에 참여하는 회원 1명(1신청 단위)이다(docs/ERD.md 4.6).
 * 최초 참여자는 별도 역할 컬럼 없이 Reservation.creatorMemberId와의 일치로 판별한다.
 * 부분 취소는 지원하지 않으며 취소·노쇼는 참여 전체에 적용된다.
 */
@Entity
@Table(
        name = "reservation_participant",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_reservation_participant_member",
                        columnNames = {"reservation_id", "member_id"}
                )
        }
)
public class ReservationParticipant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_participant_id")
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_status", nullable = false, length = 20)
    private ParticipationStatus participationStatus;

    protected ReservationParticipant() {
    }

    private ReservationParticipant(Long reservationId, Long memberId, Integer partySize) {
        this.reservationId = reservationId;
        this.memberId = memberId;
        this.partySize = partySize;
        this.participationStatus = ParticipationStatus.RESERVED;
    }

    public static ReservationParticipant create(Long reservationId, Long memberId, Integer partySize) {
        return new ReservationParticipant(reservationId, memberId, partySize);
    }

    public Long getId() {
        return id;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Integer getPartySize() {
        return partySize;
    }

    public ParticipationStatus getParticipationStatus() {
        return participationStatus;
    }
}
