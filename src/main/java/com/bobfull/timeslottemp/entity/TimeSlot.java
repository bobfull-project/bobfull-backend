package com.bobfull.timeslottemp.entity;

import com.bobfull.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 합석 테이블의 예약 가능 회차를 보관하는 Entity다(docs/ERD.md 4.4).
 * 회차 등록·조회·수정·삭제 API는 #33 범위이며, 여기서는 예약 결제 준비(#35)의
 * TimeSlot 잠금·좌석 조회에 필요한 최소 매핑만 둔다.
 */
@Entity
@Table(name = "time_slot")
public class TimeSlot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "time_slot_id")
    private Long id;

    @Column(name = "shared_table_id", nullable = false)
    private Long sharedTableId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected TimeSlot() {
    }

    private TimeSlot(Long sharedTableId, Instant startAt, Instant endAt) {
        this.sharedTableId = sharedTableId;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public static TimeSlot create(Long sharedTableId, Instant startAt, Instant endAt) {
        return new TimeSlot(sharedTableId, startAt, endAt);
    }

    public Long getId() {
        return id;
    }

    public Long getSharedTableId() {
        return sharedTableId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
