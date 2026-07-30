package com.bobfull.payment.controller;

import com.bobfull.common.exception.CustomException;
import com.bobfull.common.exception.PaymentErrorCode;
import com.bobfull.payment.service.PaymentCompletionService;
import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.WebhookVerifier;
import com.bobfull.payment.port.PortOneWebhookVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/portone")
public class PortOneWebhookController {
    private static final Logger log = LoggerFactory.getLogger(PortOneWebhookController.class);
    private final PortOneWebhookVerifier webhookVerifier;
    private final PaymentCompletionService paymentCompletionService;
    public PortOneWebhookController(PortOneWebhookVerifier webhookVerifier, PaymentCompletionService paymentCompletionService) { this.webhookVerifier = webhookVerifier; this.paymentCompletionService = paymentCompletionService; }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody String rawBody,
            @RequestHeader(value = WebhookVerifier.HEADER_ID, required = false) String id,
            @RequestHeader(value = WebhookVerifier.HEADER_SIGNATURE, required = false) String signature,
            @RequestHeader(value = WebhookVerifier.HEADER_TIMESTAMP, required = false) String timestamp) {
        try {
            if (id == null || signature == null || timestamp == null) {
                return ResponseEntity.badRequest().build();
            }
            String paymentId = webhookVerifier.verify(rawBody, id, signature, timestamp).paymentId();
            if (paymentId == null) return ResponseEntity.ok().build();
            try { paymentCompletionService.completeFromWebhook(paymentId); }
            catch (CustomException e) {
                if (e.getErrorCode() != PaymentErrorCode.PAYMENT_EXPIRED) {
                    log.error("event=PAYMENT_WEBHOOK_PERMANENT_FAILURE paymentId={} reason={}", paymentId,
                            e.getErrorCode().getCode());
                }
            }
            return ResponseEntity.ok().build();
        } catch (WebhookVerificationException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
