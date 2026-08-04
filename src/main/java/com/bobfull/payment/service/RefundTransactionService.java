package com.bobfull.payment.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.PaymentErrorCode;
import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.PaymentStatus;
import com.bobfull.payment.entity.Refund;
import com.bobfull.payment.entity.RefundStatus;
import com.bobfull.payment.repository.PaymentRepository;
import com.bobfull.payment.repository.RefundRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundTransactionService {
    private static final Logger log = LoggerFactory.getLogger(RefundTransactionService.class);
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final Clock clock;

    public RefundTransactionService(PaymentRepository paymentRepository, RefundRepository refundRepository, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RefundPreparation createRequested(Long reservationId, Long participantId) {
        Payment payment = paymentRepository.findByReservationIdAndReservationParticipantId(reservationId, participantId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        // Payment 행 락으로 동시 요청을 직렬화한다 — 락 없이 findByPayment_Id만으로 존재 여부를
        // 판단하면 두 트랜잭션이 모두 "없음"으로 보고 saveAndFlush가 payment_id UNIQUE 제약
        // 위반(원시 DB 예외)으로 끝날 수 있다. 락을 먼저 잡으면 뒤 트랜잭션은 앞 트랜잭션의
        // 커밋을 기다린 뒤 이미 생성된 Refund를 보고 REFUND_PROCESSING을 던진다.
        payment = paymentRepository.findWithLockById(payment.getId())
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        var existingRefund = refundRepository.findByPayment_Id(payment.getId());
        if (existingRefund.isPresent()) {
            Refund refund = existingRefund.get();
            if (refund.getStatus() == RefundStatus.COMPLETED) {
                return new RefundPreparation(refund, false);
            }
            if (refund.getStatus() == RefundStatus.PROCESSING || refund.getStatus() == RefundStatus.REQUESTED) {
                throw new CustomException(PaymentErrorCode.REFUND_PROCESSING);
            }
            throw new CustomException(PaymentErrorCode.REFUND_FAILED);
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new CustomException(PaymentErrorCode.PAYMENT_NOT_REFUNDABLE);
        }
        Refund refund = refundRepository.saveAndFlush(
                Refund.create(payment, payment.getAmount(), RefundStatus.REQUESTED, clock.instant(), null));
        return new RefundPreparation(refund, true);
    }

    @Transactional
    public RefundCompletion reflectExternalResult(Long refundId, String cancellationId, boolean completed) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.REFUND_ID_NOT_FOUND));
        if (completed) {
            refund.complete(cancellationId, clock.instant());
            if (refund.getPayment().getStatus() == PaymentStatus.PAID) refund.getPayment().markRefunded();
        } else {
            refund.markProcessing(cancellationId);
        }
        return RefundCompletion.from(refund);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.REFUND_ID_NOT_FOUND));
        refund.fail();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessingFromWebhook(String paymentId, String cancellationId) {
        findRefundForWebhook(paymentId, cancellationId).ifPresent(refund -> refund.markProcessing(cancellationId));
    }

    @Transactional
    public java.util.Optional<RefundCompletion> completeFromWebhook(String paymentId, String cancellationId) {
        return findRefundForWebhook(paymentId, cancellationId).map(refund -> {
            refund.complete(cancellationId, clock.instant());
            if (refund.getPayment().getStatus() == PaymentStatus.PAID) refund.getPayment().markRefunded();
            return RefundCompletion.from(refund);
        });
    }

    /**
     * cancellationId로 먼저 찾고, 없으면 paymentId로 대신 찾는다. timeout·connection reset처럼
     * PortOne 응답을 파싱하기 전에 실패한 요청은 Refund에 cancellationId가 저장된 적이 없어
     * (Refund.markProcessing/complete만 이 필드를 채운다) cancellationId만으로는 이후 도착하는
     * 웹훅과 영영 매칭되지 않는다. paymentId는 Refund 생성 시점부터 Payment에 이미 있으므로
     * 이 fallback으로 그 결과 불명확 요청도 웹훅이 회수할 수 있다.
     *
     * <p>paymentId fallback은 반드시 Refund에 cancellationId가 아직 저장된 적이 없는 경우로만
     * 한정한다. 이미 다른 cancellationId가 저장돼 있으면(예: PROCESSING으로 확정된 요청) 그
     * Refund는 fallback 대상에서 제외하고 무시한다 — 그렇지 않으면 같은 Payment에 대한 서로 다른
     * 취소 시도(웹훅)가 기존 Refund를 엉뚱한 cancellationId로 덮어쓰고 완료 처리할 수 있다.</p>
     */
    private java.util.Optional<Refund> findRefundForWebhook(String paymentId, String cancellationId) {
        var byCancellationId = refundRepository.findByCancellationId(cancellationId);
        if (byCancellationId.isPresent()) {
            return byCancellationId;
        }
        var byPaymentId = paymentRepository.findByPaymentId(paymentId).flatMap(payment -> refundRepository.findByPayment_Id(payment.getId()));
        if (byPaymentId.isPresent() && byPaymentId.get().getCancellationId() != null) {
            log.warn("event=REFUND_WEBHOOK_CANCELLATION_ID_MISMATCH paymentId={} refundId={} webhookCancellationId={} storedCancellationId={}",
                    paymentId, byPaymentId.get().getId(), cancellationId, byPaymentId.get().getCancellationId());
            return java.util.Optional.empty();
        }
        return byPaymentId;
    }

    public record RefundCompletion(RefundStatus refundStatus, Long reservationId,
                                   Long reservationParticipantId, Instant completedAt) {
        private static RefundCompletion from(Refund refund) {
            Payment payment = refund.getPayment();
            return new RefundCompletion(refund.getStatus(), payment.getReservationId(),
                    payment.getReservationParticipantId(), refund.getCompletedAt());
        }
    }

    public record RefundPreparation(Refund refund, boolean externalCallRequired) {
    }
}
