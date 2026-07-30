package com.bobfull.payment.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.PaymentErrorCode;
import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.PaymentStatus;
import com.bobfull.payment.exception.PaymentExpiredException;
import com.bobfull.payment.port.PortOnePaymentReader;
import com.bobfull.payment.repository.PaymentRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 외부 결제 검증은 트랜잭션 밖에서 수행하고, 상태 전이는 짧은 잠금 트랜잭션에 위임한다. */
@Service
public class PaymentCompletionService {
    private static final Logger log = LoggerFactory.getLogger(PaymentCompletionService.class);
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
        return completeVerified(paymentId, payment, memberId);
    }

    public PaymentCompletionTransactionService.PaymentCompletionResult completeFromWebhook(String paymentId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        if (payment.getStatus() == PaymentStatus.PAID) return new PaymentCompletionTransactionService.PaymentCompletionResult(payment, payment.getReservationId(), payment.getReservationParticipantId());
        PortOnePaymentReader.PortOnePayment external = portOnePaymentReader.read(paymentId);
        if (!paymentId.equals(external.paymentId()) || !external.paid() || external.amount() == null
                || payment.getAmount().compareTo(external.amount()) != 0
                || !Payment.CURRENCY_KRW.equals(external.currency()) || !payment.getCurrency().equals(external.currency())) {
            throw new CustomException(PaymentErrorCode.PAYMENT_VERIFICATION_FAILED);
        }
        try {
            return transactionService.complete(paymentId);
        } catch (PaymentExpiredException exception) {
                log.error("event=PAYMENT_COMPENSATION_REQUIRED paymentId={} externalStatus={} internalStatus={} expiresAt={} reason={}",
                        paymentId, "PAID", exception.getInternalStatus(), exception.getExpiresAt(), exception.getErrorCode().getCode());
            throw exception;
        }
    }

    private PaymentCompletionTransactionService.PaymentCompletionResult completeVerified(String paymentId, Payment payment, Long memberId) {
        if (payment.getStatus() == PaymentStatus.PAID) return new PaymentCompletionTransactionService.PaymentCompletionResult(payment, payment.getReservationId(), payment.getReservationParticipantId());
        if (payment.getStatus() == PaymentStatus.EXPIRED || !payment.getExpiresAt().isAfter(clock.instant())) throw new CustomException(PaymentErrorCode.PAYMENT_EXPIRED);
        if (payment.getStatus() != PaymentStatus.READY) throw new CustomException(PaymentErrorCode.PAYMENT_VERIFICATION_FAILED);
        PortOnePaymentReader.PortOnePayment external = portOnePaymentReader.read(paymentId);
        if (!paymentId.equals(external.paymentId()) || !external.paid() || external.amount() == null
                || payment.getAmount().compareTo(external.amount()) != 0
                || !Payment.CURRENCY_KRW.equals(external.currency()) || !payment.getCurrency().equals(external.currency())) {
            throw new CustomException(PaymentErrorCode.PAYMENT_VERIFICATION_FAILED);
        }
        if (!payment.getExpiresAt().isAfter(clock.instant())) {
            throw new CustomException(PaymentErrorCode.PAYMENT_EXPIRED);
        }
        return memberId == null ? transactionService.complete(paymentId) : transactionService.complete(paymentId, memberId);
    }
}
