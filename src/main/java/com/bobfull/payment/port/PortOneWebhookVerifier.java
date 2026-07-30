package com.bobfull.payment.port;

public interface PortOneWebhookVerifier {
    WebhookEvent verify(String rawBody, String id, String signature, String timestamp) throws io.portone.sdk.server.errors.WebhookVerificationException;
    record WebhookEvent(String paymentId) { }
}
