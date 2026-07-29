package com.bobfull.payment.adapter;

import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.port.ReservationConfirmationPort;
import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.PaymentErrorCode;
import org.springframework.stereotype.Component;

/** 실제 예약 확정은 #93에서 연결한다. #92에서는 완료 경계 호출만 유지한다. */
@Component
public class DeferredReservationConfirmationAdapter implements ReservationConfirmationPort {
    @Override
    public ReservationConfirmationResult confirm(Payment payment) {
        throw new CustomException(PaymentErrorCode.RESERVATION_CONFIRMATION_NOT_READY);
    }
}
