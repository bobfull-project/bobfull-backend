package com.bobfull.payment.adapter;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.PaymentErrorCode;
import com.bobfull.payment.entity.Payment;
import com.bobfull.payment.port.PortOnePaymentReader;
import com.bobfull.payment.repository.PaymentRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Issue #146 K6 성능 측정 전용 대체 구현이다. 실제 PortOne 결제 조회 API를 호출하지 않고
 * 저장된 Payment의 금액·통화를 그대로 돌려줘 {@code performance} 프로파일에서 웹훅 기반
 * PAID 확정을 실제 외부 호출 없이 재현한다. 다른 프로파일에서는 {@link PortOneSdkPaymentReader}가 그대로 쓰인다.
 */
@Component
@Profile("performance")
@Primary
public class PerformanceTestPaymentReader implements PortOnePaymentReader {

    private final PaymentRepository paymentRepository;

    public PerformanceTestPaymentReader(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public PortOnePayment read(String paymentId) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new CustomException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        return new PortOnePayment(paymentId, true, payment.getAmount(), payment.getCurrency());
    }
}
