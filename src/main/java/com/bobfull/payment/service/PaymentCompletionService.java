package com.bobfull.payment.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.PaymentErrorCode;
import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.PaymentStatus;
import com.bobfull.payment.port.PortOnePaymentReader;
import com.bobfull.payment.repository.PaymentRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;

/** 외부 결제 검증은 트랜잭션 밖에서 수행하고, 상태 전이는 짧은 잠금 트랜잭션에 위임한다. */
@Service
public class PaymentCompletionService {
    private final PaymentRepository paymentRepository;
    private final PortOnePaymentReader portOnePaymentReader;
    private final PaymentCompletionTransactionService transactionService;
    private final Clock clock;

    public PaymentCompletionService(PaymentRepository paymentRepository, PortOnePaymentReader portOnePaymentReader,
            PaymentCompletionTransactionService transactionService, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.portOnePaymentReader = portOnePaymentReader;
        this.transactionService = transactionService;
        this.clock = clock;
    }

    public PaymentCompletionTransactionService.PaymentCompletionResult complete(String paymentId, Long memberId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.isOwnedBy(memberId)) throw new CustomException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
        if (payment.getStatus() == PaymentStatus.PAID) {
            return new PaymentCompletionTransactionService.PaymentCompletionResult(payment,
                    payment.getReservationId(), payment.getReservationParticipantId());
        }
        if (payment.getStatus() != PaymentStatus.READY) {
            throw new CustomException(PaymentErrorCode.PAYMENT_VERIFICATION_FAILED);
        }
        PortOnePaymentReader.PortOnePayment external = portOnePaymentReader.read(paymentId);
        if (!paymentId.equals(external.paymentId()) || !external.paid() || external.amount() == null
                || payment.getAmount().compareTo(external.amount()) != 0
                || !Payment.CURRENCY_KRW.equals(external.currency()) || !payment.getCurrency().equals(external.currency())
                || !payment.getExpiresAt().isAfter(clock.instant())) {
            throw new CustomException(PaymentErrorCode.PAYMENT_VERIFICATION_FAILED);
        }
        return transactionService.complete(paymentId, memberId);
    }
}
