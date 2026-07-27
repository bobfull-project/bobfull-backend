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
import java.time.Instant;

/**
 * 한 회원이 예약에 신청한 인원과 참여 상태를 보관하는 Entity다(docs/ERD.md 4.6).
 * 결제 완료 후 실제 생성은 #93에서 처리한다. 예약 결제 준비(#35)는 이 Entity를
 * 현재 참여 인원 계산과 중복 참여 확인에만 사용하고 생성하지 않는다.
 */
@Entity
@Table(name = "reservation_participant")
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
    private int partySize;

    @Enumerated(EnumType.STRING)
    @Column(name = "participation_status", nullable = false, length = 20)
    private ParticipationStatus participationStatus;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    protected ReservationParticipant() {
    }

    private ReservationParticipant(Long reservationId, Long memberId, int partySize) {
        this.reservationId = reservationId;
        this.memberId = memberId;
        this.partySize = partySize;
        this.participationStatus = ParticipationStatus.RESERVED;
    }

    public static ReservationParticipant create(Long reservationId, Long memberId, int partySize) {
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

    public int getPartySize() {
        return partySize;
    }

    public ParticipationStatus getParticipationStatus() {
        return participationStatus;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
