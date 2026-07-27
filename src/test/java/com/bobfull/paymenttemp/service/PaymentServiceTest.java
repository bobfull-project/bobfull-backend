package com.bobfull.paymenttemp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bobfull.paymenttemp.dto.CreateReadyPaymentCommand;
import com.bobfull.paymenttemp.entity.Payment;
import com.bobfull.paymenttemp.entity.PaymentPurpose;
import com.bobfull.paymenttemp.entity.PaymentStatus;
import com.bobfull.paymenttemp.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-27T00:00:00Z"),
            ZoneOffset.UTC
    );

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, FIXED_CLOCK);
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void READY_결제는_현재_시각_기준_10분_뒤에_만료된다() {
        // given
        CreateReadyPaymentCommand command = new CreateReadyPaymentCommand(
                1L, 10L, null, PaymentPurpose.CREATE, 3, BigDecimal.valueOf(30000));

        // when
        Payment result = paymentService.createReadyPayment(command);

        // then
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(result.getCurrency()).isEqualTo("KRW");
        assertThat(result.getExpiresAt()).isEqualTo(FIXED_CLOCK.instant().plusSeconds(600));
    }

    @Test
    void CREATE_결제는_reservationId가_없다() {
        // given
        CreateReadyPaymentCommand command = new CreateReadyPaymentCommand(
                1L, 10L, null, PaymentPurpose.CREATE, 3, BigDecimal.valueOf(30000));

        // when
        Payment result = paymentService.createReadyPayment(command);

        // then
        assertThat(result.getReservationId()).isNull();
        assertThat(result.getPaymentPurpose()).isEqualTo(PaymentPurpose.CREATE);
    }

    @Test
    void JOIN_결제는_기존_예약_식별자를_그대로_저장한다() {
        // given
        CreateReadyPaymentCommand command = new CreateReadyPaymentCommand(
                1L, 10L, 30L, PaymentPurpose.JOIN, 2, BigDecimal.valueOf(20000));

        // when
        Payment result = paymentService.createReadyPayment(command);

        // then
        assertThat(result.getReservationId()).isEqualTo(30L);
        assertThat(result.getPaymentPurpose()).isEqualTo(PaymentPurpose.JOIN);
    }
}
