package com.bobfull.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.payment.entity.PaymentStatus;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.PaymentErrorCode;
import com.bobfull.payment.port.ReservationConfirmationPort;
import com.bobfull.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentCompletionTransactionServiceTest {
    @Mock private PaymentRepository paymentRepository;
    @Mock private ReservationConfirmationPort reservationConfirmationPort;

    @Test
    void 잠금_획득후_READY_Payment을_완료하고_Port_결과를_응답과_엔티티에_연결한다() {
        // given
        Payment payment = readyPayment();
        given(paymentRepository.findWithLockByPaymentId("payment-id")).willReturn(Optional.of(payment));
        given(reservationConfirmationPort.confirm(payment))
                .willReturn(new ReservationConfirmationPort.ReservationConfirmationResult(10L, 20L));
        PaymentCompletionTransactionService service = new PaymentCompletionTransactionService(paymentRepository,
                reservationConfirmationPort, Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));

        // when
        var result = service.complete("payment-id", 1L);

        // then
        assertThat(result.reservationId()).isEqualTo(10L);
        assertThat(result.participationId()).isEqualTo(20L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getReservationId()).isEqualTo(10L);
        assertThat(payment.getReservationParticipantId()).isEqualTo(20L);
        verify(reservationConfirmationPort).confirm(payment);
    }

    @Test
    void 락_획득_대기중_만료된_Payment은_READY를_유지하고_Port를_호출하지_않는다() {
        // given
        Payment payment = Payment.createReady("payment-id", 1L, 2L, null, PaymentPurpose.CREATE, 1,
                BigDecimal.valueOf(10000), Instant.parse("2026-07-28T00:00:00Z"));
        given(paymentRepository.findWithLockByPaymentId("payment-id")).willReturn(Optional.of(payment));
        PaymentCompletionTransactionService service = new PaymentCompletionTransactionService(paymentRepository,
                reservationConfirmationPort, Clock.fixed(Instant.parse("2026-07-28T00:01:00Z"), ZoneOffset.UTC));

        // when
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> service.complete("payment-id", 1L));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        assertThat(((CustomException) thrown).getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_VERIFICATION_FAILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verifyNoInteractions(reservationConfirmationPort);
    }

    private Payment readyPayment() {
        return Payment.createReady("payment-id", 1L, 2L, null, PaymentPurpose.CREATE, 1,
                BigDecimal.valueOf(10000), Instant.parse("2026-07-28T01:00:00Z"));
    }
}
