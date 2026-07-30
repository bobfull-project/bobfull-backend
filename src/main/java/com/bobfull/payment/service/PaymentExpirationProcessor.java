package com.bobfull.payment.service;

import com.bobfull.payment.repository.PaymentRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentExpirationProcessor {
    private final PaymentRepository paymentRepository;
    private final Clock clock;
    public PaymentExpirationProcessor(PaymentRepository paymentRepository, Clock clock) { this.paymentRepository = paymentRepository; this.clock = clock; }
    @Transactional
    public void expire(Long id) {
        paymentRepository.findWithLockById(id).ifPresent(payment -> payment.expireIfNeeded(clock.instant()));
    }
}
