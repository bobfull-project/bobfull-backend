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
 * 회차에 생성되는 합석 예약과 예약·모집 상태를 보관하는 Entity다(docs/ERD.md 4.5).
 * 결제 완료 후 실제 생성과 상태 전이는 #93에서 처리한다. 예약 결제 준비(#35)는
 * 이 Entity를 활성 예약(RECRUITING·CONFIRMED) 조회에만 사용하고 생성하지 않는다.
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

    public boolean isActive() {
        return reservationStatus == ReservationStatus.RECRUITING
                || reservationStatus == ReservationStatus.CONFIRMED;
    }

    public boolean isRecruitmentOpen() {
        return recruitmentStatus == RecruitmentStatus.OPEN;
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
