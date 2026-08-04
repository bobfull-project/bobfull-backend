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
    public void markProcessingFromWebhook(String cancellationId) {
        refundRepository.findByCancellationId(cancellationId).ifPresent(refund -> refund.markProcessing(cancellationId));
    }

    @Transactional
    public java.util.Optional<RefundCompletion> completeFromWebhook(String cancellationId) {
        return refundRepository.findByCancellationId(cancellationId).map(refund -> {
            refund.complete(cancellationId, clock.instant());
            if (refund.getPayment().getStatus() == PaymentStatus.PAID) refund.getPayment().markRefunded();
            return RefundCompletion.from(refund);
        });
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
