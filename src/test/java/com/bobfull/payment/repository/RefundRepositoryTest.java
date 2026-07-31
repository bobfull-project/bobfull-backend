package com.bobfull.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.payment.entity.Refund;
import com.bobfull.payment.entity.RefundStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class RefundRepositoryTest {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private RefundRepository refundRepository;

    @Test
    void 환불은_결제와_연결해_본인소유조건으로_조회한다() {
        // given
        Payment payment = paymentRepository.saveAndFlush(payment("payment-id", 1L));
        Refund refund = refundRepository.saveAndFlush(Refund.create(payment, BigDecimal.TEN, RefundStatus.COMPLETED,
                Instant.parse("2026-07-30T00:00:00Z"), Instant.parse("2026-07-30T00:01:00Z")));

        // when & then
        assertThat(refundRepository.findByIdAndPayment_MemberId(refund.getId(), 1L)).isPresent();
        assertThat(refundRepository.findByIdAndPayment_MemberId(refund.getId(), 2L)).isEmpty();
    }

    @Test
    void 하나의_결제에는_환불을_하나만_저장한다() {
        // given
        Payment payment = paymentRepository.saveAndFlush(payment("payment-id", 1L));
        refundRepository.saveAndFlush(Refund.create(payment, BigDecimal.TEN, RefundStatus.REQUESTED,
                Instant.parse("2026-07-30T00:00:00Z"), null));

        // when & then
        assertThatThrownBy(() -> refundRepository.saveAndFlush(Refund.create(payment, BigDecimal.TEN, RefundStatus.REQUESTED,
                Instant.parse("2026-07-30T00:00:01Z"), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Payment payment(String paymentId, Long memberId) {
        return Payment.createReady(paymentId, memberId, 2L, null, PaymentPurpose.CREATE, 1,
                BigDecimal.TEN, Instant.parse("2026-07-30T00:10:00Z"));
    }
}
