package com.bobfull.outbox.entity;

import com.bobfull.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** ChatRoom 생성 의도를 핵심 예약 확정 트랜잭션과 함께 보관하는 최소 Outbox 이벤트다. */
@Entity
@Table(name = "outbox_event",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_outbox_event_event_id", columnNames = "event_id"),
                @UniqueConstraint(name = "uk_outbox_event_aggregate", columnNames = {"event_type", "aggregate_type", "aggregate_id"})
        },
        indexes = {
                @Index(name = "idx_outbox_event_status_next_attempt",
                        columnList = "status, next_attempt_at, outbox_event_id")
        })
public class OutboxEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_event_id")
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private OutboxEventType eventType;

    @Column(name = "aggregate_type", nullable = false, length = 32)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "payload_version", nullable = false)
    private int payloadVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxEventStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "processing_token", length = 36)
    private String processingToken;

    @Column(name = "last_error_code", length = 128)
    private String lastErrorCode;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected OutboxEvent() {
    }

    private OutboxEvent(Long reservationId, Instant now) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = OutboxEventType.CHAT_ROOM_CREATION_REQUESTED;
        this.aggregateType = "RESERVATION";
        this.aggregateId = reservationId;
        this.payloadVersion = 1;
        this.status = OutboxEventStatus.PENDING;
        this.nextAttemptAt = now;
    }

    public static OutboxEvent chatRoomCreationRequested(Long reservationId, Instant now) {
        return new OutboxEvent(reservationId, now);
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public OutboxEventType getEventType() { return eventType; }
    public Long getAggregateId() { return aggregateId; }
    public OutboxEventStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getProcessingStartedAt() { return processingStartedAt; }
    public String getLastErrorCode() { return lastErrorCode; }
    public Instant getProcessedAt() { return processedAt; }

    public void retryManually(Instant now) {
        if (status != OutboxEventStatus.FAILED) {
            throw new IllegalStateException("FAILED 이벤트만 수동 재처리할 수 있습니다.");
        }
        status = OutboxEventStatus.PENDING;
        attemptCount = 0;
        nextAttemptAt = now;
        lastErrorCode = null;
    }
}
