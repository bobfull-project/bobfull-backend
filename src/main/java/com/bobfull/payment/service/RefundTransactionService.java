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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundTransactionService {
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
     */
    private java.util.Optional<Refund> findRefundForWebhook(String paymentId, String cancellationId) {
        var byCancellationId = refundRepository.findByCancellationId(cancellationId);
        if (byCancellationId.isPresent()) {
            return byCancellationId;
        }
        return paymentRepository.findByPaymentId(paymentId).flatMap(payment -> refundRepository.findByPayment_Id(payment.getId()));
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
