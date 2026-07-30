package com.bobfull.payment.adapter;

import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.port.ReservationConfirmationPort;
import com.bobfull.reservation.service.ReservationConfirmationService;

/** 실제 예약 확정은 #93에서 연결한다. #92에서는 완료 경계 호출만 유지한다. */
public class DeferredReservationConfirmationAdapter implements ReservationConfirmationPort {
    private final ReservationConfirmationService reservationConfirmationService;
    public DeferredReservationConfirmationAdapter(ReservationConfirmationService reservationConfirmationService) { this.reservationConfirmationService = reservationConfirmationService; }
    @Override
    public ReservationConfirmationResult confirm(Payment payment) {
        ReservationConfirmationService.ReservationConfirmationResult result = reservationConfirmationService.confirm(payment.getPurpose(), payment.getTimeSlotId(), payment.getReservationId(), payment.getMemberId(), payment.getPartySize());
        return new ReservationConfirmationResult(result.reservationId(), result.reservationParticipantId());
    }
}
