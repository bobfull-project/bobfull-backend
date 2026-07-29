package com.bobfull.payment.service;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.PaymentErrorCode;
import com.bobfull.payment.dto.CreateReadyPaymentCommand;
import com.bobfull.payment.dto.CreateReadyPaymentResult;
import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.payment.entity.PaymentStatus;
import com.bobfull.payment.repository.PaymentRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예약 도메인이 전달한 계산 결과로 READY Payment를 생성·저장한다.
 */
@Service
public class PaymentService implements ReadyPaymentCreator, PaymentHoldReader {

    private static final Duration READY_PAYMENT_EXPIRATION = Duration.ofMinutes(10);

    private final PaymentRepository paymentRepository;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CreateReadyPaymentResult createReadyPayment(CreateReadyPaymentCommand command) {
        Instant expiresAt = clock.instant().plus(READY_PAYMENT_EXPIRATION);
        Payment payment = Payment.createReady(
                UUID.randomUUID().toString(),
                command.memberId(),
                command.timeSlotId(),
                command.reservationId(),
                command.purpose(),
                command.partySize(),
                command.amount(),
                expiresAt
        );

        try {
            return CreateReadyPaymentResult.from(paymentRepository.saveAndFlush(payment));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(PaymentErrorCode.DUPLICATE_PAYMENT_ID);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveReadyPayment(Long timeSlotId, PaymentPurpose purpose) {
        return paymentRepository.existsByTimeSlotIdAndPurposeAndStatusAndExpiresAtAfter(
                timeSlotId, purpose, PaymentStatus.READY, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public int sumActiveReadyPartySize(Long timeSlotId) {
        return paymentRepository.sumPartySizeByTimeSlotIdAndStatusAndExpiresAtAfter(
                timeSlotId, PaymentStatus.READY, clock.instant());
    }
}
