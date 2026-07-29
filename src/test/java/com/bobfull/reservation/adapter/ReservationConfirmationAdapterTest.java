package com.bobfull.reservation.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.entity.PaymentPurpose;
import com.bobfull.payment.port.ReservationConfirmationPort;
import com.bobfull.reservation.service.ReservationConfirmationService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationConfirmationAdapterTest {

    @Mock
    private ReservationConfirmationService reservationConfirmationService;

    @Test
    void Payment_필드를_그대로_확정_서비스에_위임하고_결과를_변환한다() {
        // given
        Payment payment = Payment.createReady(
                "payment-id", 1L, 200L, null, PaymentPurpose.CREATE, 2,
                BigDecimal.valueOf(20000), Instant.parse("2026-07-25T08:10:00Z"));
        given(reservationConfirmationService.confirm(PaymentPurpose.CREATE, 200L, null, 1L, 2))
                .willReturn(new ReservationConfirmationService.ReservationConfirmationResult(10L, 20L));
        ReservationConfirmationAdapter adapter = new ReservationConfirmationAdapter(reservationConfirmationService);

        // when
        ReservationConfirmationPort.ReservationConfirmationResult result = adapter.confirm(payment);

        // then
        assertThat(result.reservationId()).isEqualTo(10L);
        assertThat(result.participationId()).isEqualTo(20L);
    }
}
