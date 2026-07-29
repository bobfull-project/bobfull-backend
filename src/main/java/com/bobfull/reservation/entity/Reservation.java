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

/**
 * 하나의 TimeSlot에 대한 합석 예약이다(docs/ERD.md 4.5).
 * 결제 검증 전에는 생성하지 않으며(ADR 0001), 취소 이력 보존을 위해 CANCELLED 상태도
 * TimeSlot 연결을 유지한다.
 */
@Entity
@Table(name = "reservation")
public class Reservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    @Column(name = "time_slot_id", nullable = false)
    private Long timeSlotId;

    @Column(name = "creator_member_id", nullable = false)
    private Long creatorMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_status", nullable = false, length = 20)
    private ReservationStatus reservationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "recruitment_status", nullable = false, length = 20)
    private RecruitmentStatus recruitmentStatus;

    protected Reservation() {
    }

    private Reservation(Long timeSlotId, Long creatorMemberId) {
        this.timeSlotId = timeSlotId;
        this.creatorMemberId = creatorMemberId;
        this.reservationStatus = ReservationStatus.RECRUITING;
        this.recruitmentStatus = RecruitmentStatus.OPEN;
    }

    public static Reservation create(Long timeSlotId, Long creatorMemberId) {
        return new Reservation(timeSlotId, creatorMemberId);
    }

    /**
     * 정원 도달 등 확정 기준을 만족했을 때 RECRUITING에서 CONFIRMED로 전이한다.
     */
    public void confirm() {
        if (reservationStatus == ReservationStatus.RECRUITING) {
            this.reservationStatus = ReservationStatus.CONFIRMED;
        }
    }

    public boolean isActive() {
        return reservationStatus == ReservationStatus.RECRUITING || reservationStatus == ReservationStatus.CONFIRMED;
    }

    public boolean isCreatedBy(Long memberId) {
        return this.creatorMemberId.equals(memberId);
    }

    public Long getId() {
        return id;
    }

    public Long getTimeSlotId() {
        return timeSlotId;
    }

    public Long getCreatorMemberId() {
        return creatorMemberId;
    }

    public ReservationStatus getReservationStatus() {
        return reservationStatus;
    }

    public RecruitmentStatus getRecruitmentStatus() {
        return recruitmentStatus;
    }
}
