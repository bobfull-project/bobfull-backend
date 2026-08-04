package com.bobfull.payment.entity;

import com.bobfull.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Payment 전체 금액에 대한 단일 환불 처리 이력이다. */
@Entity
@Table(name = "refund")
public class Refund extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancellation_id", unique = true, length = 64)
    private String cancellationId;

    protected Refund() {
    }

    private Refund(Payment payment, BigDecimal amount, RefundStatus status, Instant requestedAt, Instant completedAt) {
        this.payment = payment;
        this.amount = amount;
        this.status = status;
        this.requestedAt = requestedAt;
        this.completedAt = completedAt;
    }

    public static Refund create(Payment payment, BigDecimal amount, RefundStatus status, Instant requestedAt, Instant completedAt) {
        if (payment == null || amount == null || amount.signum() <= 0 || status == null) {
            throw new IllegalArgumentException("환불 결제, 금액, 상태는 필수입니다.");
        }
        return new Refund(payment, amount, status, requestedAt, completedAt);
    }

    public Long getId() { return id; }
    public Payment getPayment() { return payment; }
    public BigDecimal getAmount() { return amount; }
    public RefundStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public String getCancellationId() { return cancellationId; }

    public void markProcessing(String cancellationId) {
        if (status == RefundStatus.COMPLETED) return;
        this.cancellationId = cancellationId;
        this.status = RefundStatus.PROCESSING;
    }

    public void complete(String cancellationId, Instant completedAt) {
        if (status == RefundStatus.COMPLETED) return;
        this.cancellationId = cancellationId;
        this.status = RefundStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    public void fail() {
        if (status != RefundStatus.COMPLETED) this.status = RefundStatus.FAILED;
    }
}
