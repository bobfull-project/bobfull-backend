package com.bobfull.payment.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidRefundIdempotencyKeyGenerator implements RefundIdempotencyKeyGenerator {
    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
