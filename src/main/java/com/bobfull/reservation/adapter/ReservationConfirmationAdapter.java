package com.bobfull.reservation.adapter;

import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.port.ReservationConfirmationPort;
import com.bobfull.reservation.service.ReservationConfirmationService;
import org.springframework.stereotype.Component;

/**
 * 결제 완료 트랜잭션(PaymentCompletionTransactionService) 안에서 호출되어 실제
 * Reservation·ReservationParticipant를 확정한다. 호출자의 트랜잭션에 참여하도록
 * {@link ReservationConfirmationService}와 마찬가지로 자체 {@code @Transactional}을 선언하지 않는다.
 */
@Component
public class ReservationConfirmationAdapter implements ReservationConfirmationPort {

    private final ReservationConfirmationService reservationConfirmationService;

    public ReservationConfirmationAdapter(ReservationConfirmationService reservationConfirmationService) {
        this.reservationConfirmationService = reservationConfirmationService;
    }

    @Override
    public ReservationConfirmationResult confirm(Payment payment) {
        ReservationConfirmationService.ReservationConfirmationResult result = reservationConfirmationService.confirm(
                payment.getPurpose(),
                payment.getTimeSlotId(),
                payment.getReservationId(),
                payment.getMemberId(),
                payment.getPartySize()
        );
        return new ReservationConfirmationResult(result.reservationId(), result.reservationParticipantId());
    }
}
