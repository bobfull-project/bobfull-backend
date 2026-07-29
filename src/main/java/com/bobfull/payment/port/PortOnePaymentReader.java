package com.bobfull.payment.port;

import java.math.BigDecimal;

public interface PortOnePaymentReader {
    PortOnePayment read(String paymentId);

    record PortOnePayment(String paymentId, boolean paid, BigDecimal amount, String currency) {
    }
}
