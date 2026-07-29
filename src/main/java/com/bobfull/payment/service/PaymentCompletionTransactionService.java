package com.bobfull.payment.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.PaymentErrorCode;
import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.PaymentStatus;
import com.bobfull.payment.port.ReservationConfirmationPort;
import com.bobfull.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import com.bobfull.payment.port.ReservationConfirmationPort.ReservationConfirmationResult;

@Service
public class PaymentCompletionTransactionService {
    private final PaymentRepository paymentRepository;
    private final ReservationConfirmationPort reservationConfirmationPort;
    private final Clock clock;

    public PaymentCompletionTransactionService(PaymentRepository paymentRepository, ReservationConfirmationPort reservationConfirmationPort, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.reservationConfirmationPort = reservationConfirmationPort;
        this.clock = clock;
    }

    @Transactional
    public PaymentCompletionResult complete(String paymentId, Long memberId) {
        Payment payment = paymentRepository.findWithLockByPaymentId(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.isOwnedBy(memberId)) {
            throw new CustomException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            return new PaymentCompletionResult(payment, payment.getReservationId(), payment.getReservationParticipantId());
        }
        if (payment.getStatus() != PaymentStatus.READY) {
            throw new CustomException(PaymentErrorCode.PAYMENT_VERIFICATION_FAILED);
        }

        Instant now = clock.instant();
        if (!payment.getExpiresAt().isAfter(now)) {
            throw new CustomException(PaymentErrorCode.PAYMENT_VERIFICATION_FAILED);
        }
        payment.complete(now);
        ReservationConfirmationResult result = reservationConfirmationPort.confirm(payment);
        payment.attachReservationConfirmation(result.reservationId(), result.participationId());
        return new PaymentCompletionResult(payment, result.reservationId(), result.participationId());
    }

    public record PaymentCompletionResult(Payment payment, Long reservationId, Long participationId) { }
}
