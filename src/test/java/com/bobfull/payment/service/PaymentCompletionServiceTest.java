package com.bobfull.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.PaymentErrorCode;
import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.payment.port.PortOnePaymentReader;
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
class PaymentCompletionServiceTest {
    @Mock private PaymentRepository paymentRepository;
    @Mock private PortOnePaymentReader portOnePaymentReader;
    @Mock private PaymentCompletionTransactionService transactionService;

    @Test
    void 만료된_Payment은_예약확정_트랜잭션을_시작하지_않는다() {
        // given
        Payment payment = Payment.createReady("payment-id", 1L, 2L, null, PaymentPurpose.CREATE, 1,
                BigDecimal.valueOf(10000), Instant.parse("2026-07-28T00:00:00Z"));
        given(paymentRepository.findByPaymentId("payment-id")).willReturn(Optional.of(payment));
        given(portOnePaymentReader.read("payment-id"))
                .willReturn(new PortOnePaymentReader.PortOnePayment("payment-id", true, BigDecimal.valueOf(10000), "KRW"));
        PaymentCompletionService service = new PaymentCompletionService(paymentRepository, portOnePaymentReader,
                transactionService, Clock.fixed(Instant.parse("2026-07-28T00:01:00Z"), ZoneOffset.UTC));

        // when
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() -> service.complete("payment-id", 1L));

        // then
        assertThat(thrown).isInstanceOf(CustomException.class);
        assertThat(((CustomException) thrown).getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_VERIFICATION_FAILED);
        verifyNoInteractions(transactionService);
    }

    @Test
    void 이미_완료된_Payment은_PortOne_재조회와_예약확정_트랜잭션을_수행하지_않는다() {
        // given
        Payment payment = Payment.createReady("payment-id", 1L, 2L, null, PaymentPurpose.CREATE, 1,
                BigDecimal.valueOf(10000), Instant.parse("2026-07-28T01:00:00Z"));
        payment.complete(Instant.parse("2026-07-28T00:00:00Z"));
        payment.attachReservationConfirmation(10L, 20L);
        given(paymentRepository.findByPaymentId("payment-id")).willReturn(Optional.of(payment));
        PaymentCompletionService service = new PaymentCompletionService(paymentRepository, portOnePaymentReader,
                transactionService, Clock.fixed(Instant.parse("2026-07-28T00:01:00Z"), ZoneOffset.UTC));

        // when
        var result = service.complete("payment-id", 1L);

        // then
        assertThat(result.reservationId()).isEqualTo(10L);
        assertThat(result.participationId()).isEqualTo(20L);
        verifyNoInteractions(portOnePaymentReader, transactionService);
    }
}
