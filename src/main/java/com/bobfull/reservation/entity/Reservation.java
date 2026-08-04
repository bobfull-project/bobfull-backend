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
     * 확정 기준(docs/BOBFULL_API_SPEC_COMPLETE.md §0.8) 도달 시 RECRUITING에서 CONFIRMED로 전이한다.
     */
    public void confirm() {
        if (reservationStatus == ReservationStatus.RECRUITING) {
            this.reservationStatus = ReservationStatus.CONFIRMED;
        }
    }

    /**
     * 정원 도달 시 추가 참여 모집을 마감한다(§0.8).
     */
    public void closeRecruitment() {
        this.recruitmentStatus = RecruitmentStatus.CLOSED;
    }

    /**
     * 최초 예약자 취소, 확정 기준 미달로 인한 모집 마감 후 전체 취소 등으로 예약 전체를 취소한다
     * (Issue #131). TimeSlot 복구는 별도 상태 컬럼이 아니라 이 상태 전이만으로 파생된다 —
     * 활성 Reservation 조회(`existsByTimeSlotIdAndReservationStatusIn`)가 곧바로 false가 된다.
     */
    public void cancel() {
        this.reservationStatus = ReservationStatus.CANCELLED;
    }

    public boolean isCancelled() {
        return reservationStatus == ReservationStatus.CANCELLED;
    }

    /**
     * 추가 참여자 취소로 확정 기준 미달이 되면 모집이 OPEN인 동안 CONFIRMED에서 RECRUITING으로
     * 되돌린다(Issue #131). 이미 RECRUITING이면 그대로 둔다.
     */
    public void revertToRecruiting() {
        if (reservationStatus == ReservationStatus.CONFIRMED) {
            this.reservationStatus = ReservationStatus.RECRUITING;
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
