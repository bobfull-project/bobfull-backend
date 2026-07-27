package com.bobfull.paymenttemp.entity;

import com.bobfull.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * PortOne 결제와 READY 상태의 10분 임시 좌석 선점을 함께 표현하는 Entity다
 * (docs/ERD.md 4.7, docs/adr/0001-reservation-seat-consistency.md).
 * 결제 완료 검증·웹훅·PortOne 연동은 #91 범위이며, 여기서는 예약 결제 준비(#35)가
 * 호출하는 READY 생성에 필요한 최소 매핑만 둔다.
 */
@Entity
@Table(name = "payment")
public class Payment extends BaseTimeEntity {

    @Id
    @Column(name = "payment_id", length = 64)
    private String id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "time_slot_id", nullable = false)
    private Long timeSlotId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "reservation_participant_id")
    private Long reservationParticipantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_purpose", nullable = false, length = 20)
    private PaymentPurpose paymentPurpose;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    protected Payment() {
    }

    private Payment(
            String id,
            Long memberId,
            Long timeSlotId,
            Long reservationId,
            PaymentPurpose paymentPurpose,
            int partySize,
            BigDecimal amount,
            String currency,
            Instant expiresAt
    ) {
        this.id = id;
        this.memberId = memberId;
        this.timeSlotId = timeSlotId;
        this.reservationId = reservationId;
        this.paymentPurpose = paymentPurpose;
        this.partySize = partySize;
        this.amount = amount;
        this.currency = currency;
        this.paymentStatus = PaymentStatus.READY;
        this.expiresAt = expiresAt;
    }

    /**
     * CREATE는 reservationId가 아직 없는 READY 결제를 생성하고(ERD 4.7),
     * JOIN은 기존 Reservation의 식별자를 그대로 받아 READY 결제를 생성한다.
     */
    public static Payment createReady(
            String id,
            Long memberId,
            Long timeSlotId,
            Long reservationId,
            PaymentPurpose paymentPurpose,
            int partySize,
            BigDecimal amount,
            String currency,
            Instant expiresAt
    ) {
        return new Payment(id, memberId, timeSlotId, reservationId, paymentPurpose, partySize, amount, currency, expiresAt);
    }

    public String getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getTimeSlotId() {
        return timeSlotId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public Long getReservationParticipantId() {
        return reservationParticipantId;
    }

    public PaymentPurpose getPaymentPurpose() {
        return paymentPurpose;
    }

    public int getPartySize() {
        return partySize;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
